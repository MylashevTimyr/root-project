package com.example.core.service;

import java.nio.file.Path;
import java.util.stream.Stream;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FilesStorageService {
	void save(MultipartFile file);

	Resource load(String filename);

	boolean delete(String filename, String username);

	Stream<Path> loadAll(String sortBy, String filterBy, String username);

	boolean isOwner(String filename, String username);

	void saveFileInfo(String username, String filename, long fileSize);
}
