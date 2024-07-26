package com.example.mail.demo.listener;

import com.example.mail.demo.service.EmailService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

@Component
public class RabbitMQListener {

	@Autowired
	private EmailService emailService;

	@RabbitListener(queues = "${rabbitmq.queue}")
	public void receiveMessage(Map<String, String> message) {
		String type = message.get("type");
		String to = message.get("to");
		String subject = message.get("subject");
		String body = message.get("body");

		System.out.println("Received message: " + message);

		try {
			if (isValidEmail(to)) {
				emailService.sendEmail(to, subject, body);
				System.out.println("Message processed: " + message);
			} else {
				System.out.println("Invalid email address: " + to);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private boolean isValidEmail(String email) {
		String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
		Pattern pattern = Pattern.compile(emailRegex);
		if (email == null) {
			return false;
		}
		return pattern.matcher(email).matches();
	}
}


