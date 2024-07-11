package com.smsweb.sms.helper;

import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.boot.autoconfigure.task.TaskSchedulingProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;


public class FileHandleHelper {
    private final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2 MB
    private final String FILE_NAME_FORMAT_PREFIX = "ddMMyyyyhhmmss";
    private final String SCHOOL_IMG_FOLDER_PATH = new ClassPathResource("static/images/school/").getFile().getAbsolutePath();
    private final String STUDENT_IMG_FOLDER_PATH = new ClassPathResource("static/images/student/").getFile().getAbsolutePath();
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
                    }
                }
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
}
