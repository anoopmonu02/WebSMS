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
    public String[] AADHAR_SAMPLE_HEADER = {"Student Name","ID#","Father Name", "Mother Name","Mobile","Aadhar No"};
    public String[] EXAM_RESULT_SAMPLE_HEADER = {"Student Name","ID#","Father Name", "Mother Name","Mobile","SR No","Exam Name", "Exam Result Date","Total Marks","Obtained Marks","Percentage(%)","Division","Result","Remark"};

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
            if("aadhar_file".equalsIgnoreCase(fileName)){
                createSingleRow(AADHAR_SAMPLE_HEADER, 1, row);
            } else if("G_marks_entry".equalsIgnoreCase(fileName)){
                createSingleRow(EXAM_RESULT_SAMPLE_HEADER, 1, row);
            } else{
                createSingleRow(SR_SAMPLE_HEADER, 1, row);
            }

            int rowCount = 2;
            //Writing Data
            /*for(AcademicStudent student: list){
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
                if("G_marks_entry".equalsIgnoreCase(fileName)){
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue(student.getClassSrNo());
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                } else{
                    //if file is for SR or Aadhar result
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                }
            }*/
            for (AcademicStudent student : list) {
                row = sheet.createRow(rowCount++);
                colCount = 0;

                colCount = createCell(row, colCount, student.getStudent().getStudentName());
                colCount = createCell(row, colCount, student.getUuid().toString());
                colCount = createCell(row, colCount, student.getStudent().getFatherName());
                colCount = createCell(row, colCount, student.getStudent().getMotherName());
                colCount = createCell(row, colCount, student.getStudent().getMobile1());

                if ("G_marks_entry".equalsIgnoreCase(fileName)) {
                    colCount = createCell(row, colCount, student.getClassSrNo());
                    for (int i = 0; i < 9; i++) {
                        colCount = createCell(row, colCount, "");
                    }
                } else {
                    //if file is for SR or Aadhar result
                    colCount = createCell(row, colCount, "");
                }
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

    private int createCell(Row row, int colIndex, String value) {
        row.createCell(colIndex).setCellValue(value);
        return colIndex + 1;
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
                while(cellIterator.hasNext()){
                    Cell cell = cellIterator.next();
                    int colIdx = cell.getColumnIndex();
                    if (colIdx < rowData.length) {
                        rowData[colIdx] = dataFormatter.formatCellValue(cell);
                    }
                }
                excelData.add(rowData);
            }
            return excelData;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Parses the Previous Pending Balance Excel template.
     * Expected columns (row 1 = header, data from row 2):
     *   0=SNo, 1=Student Name, 2=Father Name, 3=SR No, 4=Class, 5=Pending Amount
     * Returns each data row as String[6]: [sno, studentName, fatherName, srNo, className, pendingAmount]
     */
    public List<String[]> excelOpeningBalanceDataToList(InputStream inputStream) throws IOException {
        List<String[]> result = new ArrayList<>();
        try {
            XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            int rowNum = 0;
            for (Row row : sheet) {
                if (rowNum++ == 0) continue; // skip header
                // skip empty / total rows — require at least SR No (col 3) and Pending Amount (col 5)
                Cell srCell  = row.getCell(3);
                Cell amtCell = row.getCell(5);
                if (srCell == null && amtCell == null) continue;
                String srVal = srCell  != null ? fmt.formatCellValue(srCell,  evaluator).trim() : "";
                String amVal = amtCell != null ? fmt.formatCellValue(amtCell, evaluator).trim() : "";
                if (srVal.isEmpty() || amVal.isEmpty()) continue;
                // skip rows where SR No is not numeric (e.g. "Total:" row)
                try { Double.parseDouble(amVal); } catch (NumberFormatException e) { continue; }

                String[] data = new String[6];
                data[0] = row.getCell(0) != null ? fmt.formatCellValue(row.getCell(0), evaluator).trim() : "";
                data[1] = row.getCell(1) != null ? fmt.formatCellValue(row.getCell(1), evaluator).trim() : "";
                data[2] = row.getCell(2) != null ? fmt.formatCellValue(row.getCell(2), evaluator).trim() : "";
                data[3] = srVal;
                data[4] = row.getCell(4) != null ? fmt.formatCellValue(row.getCell(4), evaluator).trim() : "";
                data[5] = amVal;
                result.add(data);
            }
            workbook.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public List<String[]> excelExamResultDataToList(InputStream inputStream, int dataStartRowNumber) throws IOException{
        List<String[]> excelData = new ArrayList<>();
        try{
            int rowNumber = 0;
            XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            // FormulaEvaluator computes formula cell values instead of returning the formula string
            org.apache.poi.ss.usermodel.FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter dataFormatter = new DataFormatter();
            Iterator<Row> iterator = sheet.iterator();
            while(iterator.hasNext()){
                Row row = iterator.next();
                if(rowNumber < dataStartRowNumber){
                    rowNumber++;
                    continue;
                }

                Iterator<Cell> cellIterator = row.iterator();
                String[] rowData = new String[15];
                if(row.getPhysicalNumberOfCells() > 10){
                    while(cellIterator.hasNext()){
                        Cell cell = cellIterator.next();
                        int colIdx = cell.getColumnIndex();
                        if (colIdx < rowData.length) {
                            // Use evaluator so formula cells return computed value, not formula string
                            rowData[colIdx] = dataFormatter.formatCellValue(cell, evaluator);
                        }
                    }
                    excelData.add(rowData);
                }
            }
            workbook.close();
            return excelData;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }



}
