package com.example.core.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;
import javax.sql.DataSource;

@Service
public class FilesStorageServiceImpl implements FilesStorageService {

	@Autowired
	private DataSource dataSource;

	private final Path root = Paths.get("uploads");


	@Override
	public void save(MultipartFile file) {
		try {
			Files.copy(file.getInputStream(), this.root.resolve(Objects.requireNonNull(file.getOriginalFilename())));
		} catch (Exception e) {
			if (e instanceof FileAlreadyExistsException) {
				throw new RuntimeException("A file of that name already exists.");
			}
			throw new RuntimeException(e.getMessage());
		}
	}

	@Override
	public Resource load(String filename) {
		try {
			Path file = root.resolve(filename);
			Resource resource = new UrlResource(file.toUri());
			if (resource.exists() || resource.isReadable()) {
				return resource;
			} else {
				throw new RuntimeException("Could not read the file!");
			}
		} catch (MalformedURLException e) {
			throw new RuntimeException("Error: " + e.getMessage());
		}
	}

	@Override
	public boolean delete(String filename, String username) {
		try (Connection connection = dataSource.getConnection()) {
			Path file = root.resolve(filename);
			boolean deletedFromFS = Files.deleteIfExists(file);

			if (deletedFromFS) {
				String sql = "DELETE FROM user_files WHERE filename = ? AND username = ?";
				try (PreparedStatement statement = connection.prepareStatement(sql)) {
					statement.setString(1, filename);
					statement.setString(2, username);
					return statement.executeUpdate() > 0;
				}
			}
			return false;
		} catch (SQLException | IOException e) {
			throw new RuntimeException("Error deleting file", e);
		}
	}


	@Override
	public Stream<Path> loadAll(String sortBy, String filterBy, String username) {
		try (Connection connection = dataSource.getConnection()) {
			StringBuilder sql = new StringBuilder("SELECT filename FROM user_files WHERE username = ?");
			if (filterBy != null && !filterBy.isEmpty()) {
				sql.append(" AND ").append(filterBy);
			}
			if (sortBy != null && !sortBy.isEmpty()) {
				sql.append(" ORDER BY ").append(sortBy);
			}

			try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
				statement.setString(1, username);
				ResultSet resultSet = statement.executeQuery();
				List<String> filenames = new ArrayList<>();
				while (resultSet.next()) {
					filenames.add(resultSet.getString("filename"));
				}
				return filenames.stream()
						.map(root::resolve)
						.filter(path -> Files.exists(path) && Files.isReadable(path));
			}
		} catch (SQLException e) {
			throw new RuntimeException("Could not load the files for user: " + username, e);
		}
	}

	@Override
	public boolean isOwner(String filename, String username) {
		try (Connection connection = dataSource.getConnection()) {
			String sql = "SELECT COUNT(*) FROM user_files WHERE filename = ? AND username = ?";
			try (PreparedStatement statement = connection.prepareStatement(sql)) {
				statement.setString(1, filename);
				statement.setString(2, username);
				ResultSet resultSet = statement.executeQuery();
				return resultSet.next() && resultSet.getInt(1) > 0;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Error checking file owner", e);
		}
	}

	@Override
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

