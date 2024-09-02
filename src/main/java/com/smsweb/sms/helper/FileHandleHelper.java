package com.smsweb.sms.helper;

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
import java.util.UUID;


@Component
public class FileHandleHelper {
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

    public FileHandleHelper() throws IOException
    {
    }

    public String copyImageToGivenDirectory(MultipartFile logo, String imageFolder){
        String fileName = "";
        try{
            if(!logo.isEmpty()){
                System.out.println("==== "+logo.getContentType());
                boolean isSizeOrTypeValid = checkValidImageFileAndSize(logo);
                if(isSizeOrTypeValid){
                    String fileFormatName = new SimpleDateFormat(FILE_NAME_FORMAT_PREFIX).format(new Date());
                    String imageFileName = fileFormatName + "_" + logo.getOriginalFilename();
                    Path path;
                    if(imageFolder.equalsIgnoreCase("school")){
                        path = Paths.get(SCHOOL_IMG_FOLDER_PATH + File.separator + imageFileName);
                        System.out.println("path: "+path);
                        long l = Files.copy(logo.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
                        fileName = "Success";
                    } else if(imageFolder.equalsIgnoreCase("students")){
                        path = Paths.get(STUDENT_IMG_FOLDER_PATH + File.separator + imageFileName);
                        System.out.println("path: "+path);
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
