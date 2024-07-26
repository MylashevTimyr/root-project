package com.example.core.service;


import com.example.core.models.User;
import com.example.core.repository.UserRepository;
import com.example.core.request.SignupRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RegistrationService {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EventPublisherService eventPublisherService;

	@Autowired
	private PasswordEncoder encoder;

	public void registerUser(SignupRequest signUpRequest) {
		if (userRepository.existsByUsername(signUpRequest.getUsername()) || userRepository.existsByEmail(signUpRequest.getEmail())) {
			throw new RuntimeException("User already exists");
		}

		User user = new User();
		user.setUsername(signUpRequest.getUsername());
		user.setEmail(signUpRequest.getEmail());
		user.setPassword(encoder.encode(signUpRequest.getPassword()));
		user.setRole("ROLE_USER");
		userRepository.save(user);

		sendRegistrationEvent(user.getEmail());
	}

	private void sendRegistrationEvent(String email) {
		Map<String, String> message = new HashMap<>();
		message.put("to", email);
		message.put("subject", "Registration Successful");
		message.put("body", "You have successfully registered.");

		eventPublisherService.publishEvent(message);
	}
}
