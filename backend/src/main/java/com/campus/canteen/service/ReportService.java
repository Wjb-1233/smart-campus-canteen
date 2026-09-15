package com.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.entity.Order;
import com.campus.canteen.entity.OrderItem;
import com.campus.canteen.mapper.OrderItemMapper;
import com.campus.canteen.mapper.OrderMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    public byte[] dailyExcel(Long stallId) {
        List<Order> orders = loadOrders(stallId);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("日结报表");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("订单号");
            header.createCell(1).setCellValue("档口ID");
            header.createCell(2).setCellValue("金额");
            header.createCell(3).setCellValue("热量");
            header.createCell(4).setCellValue("状态");
            header.createCell(5).setCellValue("下单时间");
            int r = 1;
            for (Order o : orders) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(o.getOrderNo());
                row.createCell(1).setCellValue(o.getStallId());
                row.createCell(2).setCellValue(o.getTotalAmount().doubleValue());
                row.createCell(3).setCellValue(o.getTotalCalorie());
                row.createCell(4).setCellValue(o.getStatus());
                row.createCell(5).setCellValue(String.valueOf(o.getCreatedAt()));
            }
            Sheet itemSheet = wb.createSheet("明细");
            Row ih = itemSheet.createRow(0);
            ih.createCell(0).setCellValue("订单ID");
            ih.createCell(1).setCellValue("菜品");
            ih.createCell(2).setCellValue("数量");
            ih.createCell(3).setCellValue("单价");
            int ir = 1;
            for (Order o : orders) {
                List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, o.getId()));
                for (OrderItem item : items) {
                    Row row = itemSheet.createRow(ir++);
                    row.createCell(0).setCellValue(o.getId());
                    row.createCell(1).setCellValue(item.getDishName());
                    row.createCell(2).setCellValue(item.getQuantity());
                    row.createCell(3).setCellValue(item.getUnitPrice().doubleValue());
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("导出失败: " + e.getMessage(), e);
        }
    }

    public byte[] dailyPdf(Long stallId) {
        List<Order> orders = loadOrders(stallId);
        BigDecimal total = orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            BaseFont bf = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            Font titleFont = new Font(bf, 16, Font.BOLD);
            Font bodyFont = new Font(bf, 11, Font.NORMAL);
            doc.add(new Paragraph("智慧校园食堂日结报告", titleFont));
            doc.add(new Paragraph("日期: " + LocalDate.now() + "  档口: " + (stallId == null ? "全部" : stallId), bodyFont));
            doc.add(new Paragraph("订单数: " + orders.size() + "  营业额: ¥" + total, bodyFont));
            doc.add(new Paragraph(" ", bodyFont));
            for (Order o : orders) {
                doc.add(new Paragraph(o.getOrderNo() + " | 档口" + o.getStallId()
                        + " | ¥" + o.getTotalAmount() + " | " + o.getStatus(), bodyFont));
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF导出失败: " + e.getMessage(), e);
        }
    }

    private List<Order> loadOrders(Long stallId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<Order>()
                .ge(Order::getCreatedAt, start)
                .notIn(Order::getStatus, "CANCELLED", "CREATED");
        if (stallId != null) {
            qw.eq(Order::getStallId, stallId);
        }
        return orderMapper.selectList(qw);
    }
}
