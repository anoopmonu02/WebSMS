package com.smsweb.sms.helper;

import com.smsweb.sms.models.student.AcademicStudent;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
public class ExcelFileHandler {
    public String[] GRADE_HEADER = {"Medium","Grade","Section"};
    public String[] GRADE_HEADER_FULL = {"All Student List"};
    public String[] SR_SAMPLE_HEADER = {"Student Name","ID#","Father Name", "Mother Name","Mobile","SR No"};

    public ByteArrayInputStream LoadSampleSRFile(String fileName, List<AcademicStudent> list, String[] medium_grade_section, String fileType) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try{
            //Create sheet
            Sheet sheet = workbook.createSheet("Student List");

            Row row = sheet.createRow(0);
            int colCount = 0;
            if("F".equalsIgnoreCase(fileType)){
                for (int i=0;i<GRADE_HEADER_FULL.length;i++){
                    Cell keyCell = row.createCell(colCount);
                    keyCell.setCellValue(GRADE_HEADER_FULL[i]);
                }
            } else{
                for (int i=0;i<GRADE_HEADER.length;i++){
                    Cell keyCell = row.createCell(colCount);
                    keyCell.setCellValue(GRADE_HEADER[i]);
                    colCount++;
                    Cell valueCell = row.createCell(colCount);
                    valueCell.setCellValue(medium_grade_section[i]);
                    colCount++;
                }
            }

            //Create Main Header
            row = sheet.createRow(1);
            createSingleRow(SR_SAMPLE_HEADER, 1, row);
            int rowCount = 2;
            //Writing Data
            for(AcademicStudent student: list){
                row = sheet.createRow(rowCount);
                colCount = 0;
                rowCount++;
                Cell dataCell = row.createCell(colCount); colCount++;
                dataCell.setCellValue(student.getStudent().getStudentName());
                dataCell = row.createCell(colCount); colCount++;
                dataCell.setCellValue(student.getUuid().toString());
                dataCell = row.createCell(colCount); colCount++;
                dataCell.setCellValue(student.getStudent().getFatherName());
                dataCell = row.createCell(colCount);  colCount++;
                dataCell.setCellValue(student.getStudent().getMotherName());
                dataCell = row.createCell(colCount);  colCount++;
                dataCell.setCellValue(student.getStudent().getMobile1());
                dataCell = row.createCell(colCount);  colCount++;
                dataCell.setCellValue("");
            }
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
        finally {
            workbook.close();
            out.flush();
            out.close();
        }
    }

    public Row createSingleRow(String[] rowData, int rowIndex, Row row){
        try{
            for(int i=0;i<rowData.length;i++){
                Cell cell = row.createCell(i);
                cell.setCellValue(rowData[i]);
            }
            return row;
        }catch(Exception e){
            return null;
        }
    }

    public boolean checkValidExcelFormat(MultipartFile excelFile){
        try{
            String contentType = excelFile.getContentType();
            String fileName = excelFile.getOriginalFilename();

            if((fileName != null && (fileName.endsWith(".xls") || fileName.endsWith(".xlsx"))) &&
                    (contentType != null && (contentType.equals("application/vnd.ms-excel") ||
                            contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))){
                return true;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return false;
    }
    public List<String[]> excelDataToList(InputStream inputStream, int dataStartRowNumber) throws IOException{
        List<String[]> excelData = new ArrayList<>();
        try{
            int rowNumber = 0;
            XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> iterator = sheet.iterator();
            DataFormatter dataFormatter = new DataFormatter();
            while(iterator.hasNext()){
                Row row = iterator.next();
                if(rowNumber<dataStartRowNumber){
                    rowNumber++;
                    //check for header
                    continue;
                }

                Iterator<Cell> cellIterator = row.iterator();
                String[] rowData = new String[7];
                int colNumber = 0;
                while(cellIterator.hasNext()){
                    Cell cell = cellIterator.next();
                    rowData[colNumber] = dataFormatter.formatCellValue(cell);//cell.getStringCellValue();
                    colNumber++;
                }
                excelData.add(rowData);
            }
            return excelData;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }



}