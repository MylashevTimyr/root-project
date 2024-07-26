package com.example.core.service;

import com.example.core.models.User;
import com.example.core.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
  @Autowired
  UserRepository userRepository;

  @Autowired
  private DataSource dataSource;

  private final Path root = Paths.get("uploads");

  private static final Logger logger = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

  @Override
  @Transactional
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    logger.info("Attempting to load user by username: {}", username);

    User user = getUserByUsername(username);
    if (user == null) {
      logger.warn("User not found with username: {}", username);
      return null;
    }

    List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(user.getRole()));

    return UserDetailsImpl.build(user);
  }

  private User getUserByUsername(String username) {
    return userRepository.findByUsername(username).orElse(null);
  }

  public void saveFileInfo(String username, String filename, long fileSize) {
    try (Connection connection = dataSource.getConnection()) {
      String sql = "INSERT INTO user_files (username, filename, url, file_size, user_id) VALUES (?, ?, ?, ?, ?)";
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, username);
        statement.setString(2, filename);
        statement.setString(3, root.resolve(filename).toString());
        statement.setLong(4, fileSize);
        statement.setLong(5, getUserIdByUsername(username));
        statement.executeUpdate();
      }
    } catch (SQLException e) {
      throw new RuntimeException("Error saving file info", e);
    }
  }

  private Long getUserIdByUsername(String username) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      String sql = "SELECT id FROM users WHERE username = ?";
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, username);
        try (ResultSet resultSet = statement.executeQuery()) {
          if (resultSet.next()) {
            return resultSet.getLong("id");
          } else {
            throw new RuntimeException("User not found");
          }
        }
      }
    }
  }
}

