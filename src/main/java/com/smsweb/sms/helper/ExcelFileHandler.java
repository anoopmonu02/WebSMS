package com.smsweb.sms.helper;

import com.smsweb.sms.models.student.AcademicStudent;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class ExcelFileHandler {
    public String[] GRADE_HEADER = {"Medium","Grade","Section"};
    public String[] SR_SAMPLE_HEADER = {"Student Name","Father Name", "Mother Name","Mobile","SR No"};

    public String LoadSampleSRFile(String fileName, List<AcademicStudent> list, String[] medium_grade_section) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        FileOutputStream out = new FileOutputStream(fileName);
        try{
            //Create sheet
            Sheet sheet = workbook.createSheet("Student List");

            Row row = sheet.createRow(0);
            int colCount = 0;
            for (int i=0;i<GRADE_HEADER.length;i++){
                Cell keyCell = row.createCell(colCount);
                keyCell.setCellValue(GRADE_HEADER[i]);
                colCount++;
                Cell valueCell = row.createCell(colCount);
                valueCell.setCellValue(medium_grade_section[i]);
            }
            //Create Main Header
            row = sheet.createRow(1);
            createSingleRow(SR_SAMPLE_HEADER, 1, row);

            //Writing Data
            row = sheet.createRow(2);
            for(AcademicStudent student: list){
                colCount = 0;
                Cell dataCell = row.createCell(colCount); colCount++;
                dataCell.setCellValue(student.getStudent().getStudentName()); colCount++;
                dataCell = row.createCell(colCount); colCount++;
                dataCell.setCellValue(student.getStudent().getFatherName()); colCount++;
                dataCell = row.createCell(colCount);  colCount++;
                dataCell.setCellValue(student.getStudent().getMotherName()); colCount++;
                dataCell = row.createCell(colCount);  colCount++;
                dataCell.setCellValue(student.getStudent().getMobile1()); colCount++;
                dataCell = row.createCell(colCount);  colCount++;
                dataCell.setCellValue("");
            }
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="+fileName);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            workbook.write(out);
            return "success";

        }catch(Exception e){
            e.printStackTrace();
            return "error";
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

}
