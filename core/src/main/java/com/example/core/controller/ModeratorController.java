package com.example.core.controller;

import com.example.core.models.User;
import com.example.core.response.MessageResponse;
import com.example.core.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/moderator")
@PreAuthorize("hasRole('MODERATOR')")
public class ModeratorController {

	@Autowired
	private UserRepository userRepository;

	@PutMapping("/users/{userId}/block")
	public ResponseEntity<?> blockUser(@PathVariable Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new RuntimeException("Error: User is not found."));

		if (user.isBlocked()) {
			return ResponseEntity.badRequest().body(new MessageResponse("Error: User is already blocked."));
		}

		user.setBlocked(true);
		userRepository.save(user);

		return ResponseEntity.ok(new MessageResponse("User blocked successfully!"));
	}

	@PutMapping("/users/{userId}/unblock")
	public ResponseEntity<?> unblockUser(@PathVariable Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new RuntimeException("Error: User is not found."));

		if (!user.isBlocked()) {
			return ResponseEntity.badRequest().body(new MessageResponse("Error: User is not blocked."));
		}

		user.setBlocked(false);
		userRepository.save(user);

		return ResponseEntity.ok(new MessageResponse("User unblocked successfully!"));
	}
}
