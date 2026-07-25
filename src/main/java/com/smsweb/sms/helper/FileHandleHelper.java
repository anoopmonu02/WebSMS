package com.smsweb.sms.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;


@Component
public class FileHandleHelper {
    private static final Logger log = LoggerFactory.getLogger(FileHandleHelper.class);
    private final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2 MB
    private final String FILE_NAME_FORMAT_PREFIX = "ddMMyyyyhhmmss";
    /*private final String SCHOOL_IMG_FOLDER_PATH = new ClassPathResource("static/school/").getFile().getAbsolutePath();
    private final String STUDENT_IMG_FOLDER_PATH = new ClassPathResource("static/students/").getFile().getAbsolutePath();
    private final String EMPLOYEE_IMG_FOLDER_PATH = new ClassPathResource("static/students/").getFile().getAbsolutePath();*/
    @Value("${student.image.storage.path}")
    private String STUDENT_IMG_FOLDER_PATH;

    @Value("${employee.image.storage.path}")
    private String EMPLOYEE_IMG_FOLDER_PATH;

    @Value("${school.image.storage.path}")
    private String SCHOOL_IMG_FOLDER_PATH;

    @Value("${customer.image.storage.path}")
    private String CUSTOMER_IMG_FOLDER_PATH;

    @Value("${message.attachment.storage.path}")
    private String MESSAGE_ATTACHMENT_FOLDER_PATH;

    // ── Notification attachments ("Send Document") ──────────────────────────
    // Broader than the 2MB/image-only cap above — scanned documents and
    // multi-page PDFs are realistically bigger than a profile picture.
    // Checked against BOTH the declared content-type AND the filename
    // extension (two independent, easily-spoofed-individually signals) —
    // not full magic-byte sniffing, but meaningfully harder to fool than
    // trusting the browser's Content-Type header alone.
    private static final long MAX_ATTACHMENT_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_ATTACHMENT_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );
    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    public FileHandleHelper() throws IOException
    {
    }

    /**
     * Validates a file for the notification-attachment upload — same intent as
     * checkValidImageFileAndSize() above, but this one accepts documents too,
     * not just images. Separate method rather than widening the existing one:
     * checkValidImageFileAndSize() is still used by the student/employee/school/
     * customer photo uploads, which must stay image-only — this must never
     * change what those accept.
     */
    public boolean isValidAttachmentFile(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) return false;
            if (file.getSize() > MAX_ATTACHMENT_FILE_SIZE) return false;

            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_ATTACHMENT_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
                return false;
            }

            String originalName = file.getOriginalFilename();
            if (originalName == null || !originalName.contains(".")) return false;
            String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            return ALLOWED_ATTACHMENT_EXTENSIONS.contains(ext);
        } catch (Exception e) {
            log.warn("Attachment validation error", e);
            return false;
        }
    }

    /**
     * Saves one notification attachment under a per-school subfolder (keeps a
     * single school's files groupable/purgeable without touching another
     * school's, and avoids one giant flat directory over time). Filename is
     * UUID-prefixed, same convention as saveImage() below — unguessable, never
     * collides. Caller (SmsMessageController) must call isValidAttachmentFile()
     * first; this method assumes the file has already been validated.
     *
     * @return the stored filename, relative to MESSAGE_ATTACHMENT_FOLDER_PATH,
     *         e.g. "42/3f1a9722-....png" — this is what gets persisted as
     *         SmsMessageAttachment.storedFileName.
     */
    public String saveMessageAttachment(MultipartFile file, Long schoolId) throws IOException {
        log.info("Inside saveMessageAttachment - schoolId={}", schoolId);
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String storedName = UUID.randomUUID() + "_" + originalName;
        String relativePath = schoolId + File.separator + storedName;

        Path folder = Paths.get(MESSAGE_ATTACHMENT_FOLDER_PATH, String.valueOf(schoolId));
        File folderFile = folder.toFile();
        if (!folderFile.exists() && !folderFile.mkdirs()) {
            throw new IOException("Failed to create attachment directory: " + folder);
        }

        Path target = folder.resolve(storedName);
        Files.write(target, file.getBytes());
        return relativePath;
    }

    /**
     * Resolves a stored attachment's absolute file on disk, with the same
     * path-traversal protection used by the mobile profile-pic endpoint
     * (MobileStudentController.getProfilePic): canonicalize both the base
     * folder and the requested file, then verify the requested file is
     * actually inside the base folder before returning it.
     *
     * @return the resolved File, or null if it doesn't exist or resolves
     *         outside the attachments folder (path traversal attempt).
     */
    public File resolveMessageAttachmentFile(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) return null;
        File base = new File(MESSAGE_ATTACHMENT_FOLDER_PATH).getCanonicalFile();
        File file = new File(base, relativePath).getCanonicalFile();
        if (!file.getPath().startsWith(base.getPath() + File.separator)) {
            log.warn("Path traversal attempt blocked resolving attachment: {}", relativePath);
            return null;
        }
        return file.exists() ? file : null;
    }

    public String copyImageToGivenDirectory(MultipartFile logo, String imageFolder){
        log.info("Inside copyImageToGivenDirectory - imageFolder={}", imageFolder);
        String fileName = "";
        try{
            if(!logo.isEmpty()){
                log.debug("Uploaded file content-type: {}", logo.getContentType());
                boolean isSizeOrTypeValid = checkValidImageFileAndSize(logo);
                if(isSizeOrTypeValid){
                    String fileFormatName = new SimpleDateFormat(FILE_NAME_FORMAT_PREFIX).format(new Date());
                    String imageFileName = fileFormatName + "_" + logo.getOriginalFilename();
                    Path path;
                    if(imageFolder.equalsIgnoreCase("school")){
                        path = Paths.get(SCHOOL_IMG_FOLDER_PATH + File.separator + imageFileName);
                        log.debug("Saving school image to: {}", path);
                        long l = Files.copy(logo.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
                        fileName = "Success";
                    } else if(imageFolder.equalsIgnoreCase("students")){
                        path = Paths.get(STUDENT_IMG_FOLDER_PATH + File.separator + imageFileName);
                        log.debug("Saving student image to: {}", path);
                        long l = Files.copy(logo.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
                        fileName = "Success";
                    }
                } else{
                    fileName = "Either image format not supported or size exceeded 2MB.";
                }
            } else{
                fileName = "Success_no_image";
            }
        }catch(Exception ex){
            ex.printStackTrace();
            fileName = "Fail";
        }
        return fileName;
    }

    public boolean checkValidImageFileAndSize(MultipartFile logo){
        boolean validFile = true;
        try{
            if (!logo.getContentType().startsWith("image/")){
                validFile = false;
            }
            if(logo.getSize() > MAX_FILE_SIZE){
                validFile = false;
            }
        }catch(Exception e){
            e.printStackTrace();
            validFile = false;
        }
        return validFile;
    }

    public String saveImage(String imageFolderName, MultipartFile imageFile) throws IOException {
        log.info("Inside saveImage - imageFolderName={}", imageFolderName);
        String fileName = "";
        try{
            if(!imageFile.isEmpty()){
                boolean isSizeOrTypeValid = checkValidImageFileAndSize(imageFile);
                if(isSizeOrTypeValid){
                    //String fileFormatName = new SimpleDateFormat(FILE_NAME_FORMAT_PREFIX).format(new Date());
                    String fileFormatName = UUID.randomUUID().toString();
                    String imageFileName = fileFormatName + "_" + imageFile.getOriginalFilename();
                    if(imageFolderName.equalsIgnoreCase("school")){
                        Path savedImageFile = saveImageInDirectory(SCHOOL_IMG_FOLDER_PATH, imageFileName, imageFile);
                        return imageFileName;
                    } else if (imageFolderName.equalsIgnoreCase("student")) {
                        Path savedImageFile = saveImageInDirectory(STUDENT_IMG_FOLDER_PATH, imageFileName, imageFile);
                        return imageFileName;
                    } else if (imageFolderName.equalsIgnoreCase("employee")) {
                        Path savedImageFile = saveImageInDirectory(EMPLOYEE_IMG_FOLDER_PATH, imageFileName, imageFile);
                        return imageFileName;
                    } else if (imageFolderName.equalsIgnoreCase("customer")) {
                        Path savedImageFile = saveImageInDirectory(CUSTOMER_IMG_FOLDER_PATH, imageFileName, imageFile);
                        return imageFileName;
                    } else{
                        fileName = "Specified category not valid";
                    }
                } else{
                    fileName = "Either image format not supported or size exceeded 2MB.";
                }
            } else{
                fileName = "Success_no_image";
            }
        }catch(Exception e){
            e.printStackTrace();
            fileName = "Failed to save the image: "+e.getLocalizedMessage();
        }
        return fileName;
    }

    private Path saveImageInDirectory(String folderPath, String imageFileName, MultipartFile imageFile) throws IOException {
        Path path = Paths.get(folderPath, imageFileName);
        File directory = new File(folderPath);
        try {
            // Ensure the directory exists
            if (!directory.exists()) {
                boolean created = directory.mkdirs();
                if (!created) {
                    throw new IOException("Failed to create the directory: " + folderPath);
                }
            }
            // Save the image file
            Files.write(path, imageFile.getBytes());
            return path;

        } catch (IOException e) {
            throw new IOException("Failed to save the image file: " + imageFileName, e);
        }
    }


}
