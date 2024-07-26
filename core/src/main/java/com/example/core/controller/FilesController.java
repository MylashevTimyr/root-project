package com.example.core.controller;

import com.example.core.models.FileInfo;
import com.example.core.response.MessageResponse;

import com.example.core.repository.UserRepository;
import com.example.core.service.EventPublisherService;
import com.example.core.service.FilesStorageService;
import com.example.core.service.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/api")
public class FilesController {

	@Autowired
	FilesStorageService storageService;

	@Autowired
	UserRepository userRepository;

	@Autowired
	private EventPublisherService eventPublisherService;

	@PostMapping("/upload")
	public ResponseEntity<MessageResponse> uploadFiles(@RequestParam("files") List<MultipartFile> files, Principal principal) {
		String message = "";
		long totalSize = 0;
		try {
			for (MultipartFile file : files) {
				if (!file.getContentType().equals("image/jpeg") && !file.getContentType().equals("image/png")) {
					message = "Only JPG and PNG files are allowed!";
					return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new MessageResponse(message));
				}
				if (file.getSize() > 10 * 1024 * 1024) {
					message = "File size exceeds limit!";
					return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(new MessageResponse(message));
				}
				storageService.save(file);
				totalSize += file.getSize();

				storageService.saveFileInfo(principal.getName(), file.getOriginalFilename(), file.getSize());
			}
			message = "Uploaded files successfully.";

			String userEmail = userRepository.findByUsername(principal.getName()).get().getEmail();

			Map<String, String> eventMessage = new HashMap<>();
			eventMessage.put("type", "upload");
			eventMessage.put("to", userEmail);
			eventMessage.put("subject", "Images Uploaded");
			eventMessage.put("body", "Total size of uploaded images: " + totalSize + " bytes");
			eventPublisherService.publishEvent(eventMessage);

			return ResponseEntity.status(HttpStatus.OK).body(new MessageResponse(message));
		} catch (Exception e) {
			message = "Could not upload files: " + e.getMessage();
			return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(new MessageResponse(message));
		}
	}

	@GetMapping("/files/{filename:.+}")
	public ResponseEntity<Resource> getFile(@PathVariable String filename, Principal principal) throws IOException {
		Resource file = storageService.load(filename);
		if (file == null || (principal != null && !storageService.isOwner(filename, principal.getName()))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
		}

		UserDetailsImpl userDetails = (UserDetailsImpl) ((Authentication) principal).getPrincipal();
		String userEmail = userDetails.getEmail();

		Map<String, String> eventMessage = new HashMap<>();
		eventMessage.put("type", "download");
		eventMessage.put("to", userEmail);
		eventMessage.put("subject", "File Downloaded");
		eventMessage.put("body", "File downloaded: " + filename);
		eventPublisherService.publishEvent(eventMessage);

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"").body(file);
	}

	@GetMapping("/files")
	public ResponseEntity<List<FileInfo>> getListFiles(
			@RequestParam(required = false) String sortBy,
			@RequestParam(required = false) String filterBy,
			Principal principal) {

		List<FileInfo> fileInfos = storageService.loadAll(sortBy, filterBy, principal != null ? principal.getName() : null).map(path -> {
			String filename = path.getFileName().toString();
			String url = MvcUriComponentsBuilder
					.fromMethodName(FilesController.class, "getFile", filename, principal)
					.build()
					.toString();

			return new FileInfo(filename, url);
		}).collect(Collectors.toList());

		return ResponseEntity.status(HttpStatus.OK).body(fileInfos);
	}

	@DeleteMapping("/files/{filename:.+}")
	public ResponseEntity<MessageResponse> deleteFile(@PathVariable String filename, Principal principal) {
		String message = "";
		try {
			boolean existed = storageService.delete(filename, principal.getName());
			if (existed) {
				message = "Deleted the file successfully: " + filename;
				return ResponseEntity.status(HttpStatus.OK).body(new MessageResponse(message));
			}
			message = "The file does not exist!";
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse(message));
		} catch (Exception e) {
			message = "Could not delete the file: " + filename + ". Error: " + e.getMessage();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse(message));
		}
	}

	private void sendImageUploadEvent(String email, long totalSize) {
		Map<String, String> message = new HashMap<>();
		message.put("to", email);
		message.put("subject", "Images Uploaded");
		message.put("body", "Total size of uploaded images: " + totalSize + " bytes");

		eventPublisherService.publishEvent(message);
	}
}

