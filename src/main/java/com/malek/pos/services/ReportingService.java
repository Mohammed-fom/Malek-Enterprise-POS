package com.malek.pos.services;

import com.malek.pos.database.ReportingDAO;
import com.malek.pos.models.reporting.ReportingDTOs.*;
import com.malek.pos.utils.EventBus;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

public class ReportingService {

    private final ReportingDAO dao;
    private final com.malek.pos.database.LaybyDAO laybyDAO;

    public ReportingService() {
        this.dao = new ReportingDAO();
        this.laybyDAO = new com.malek.pos.database.LaybyDAO();
    }

    // --- Async Data Fetching ---

    public void fetchDashboardData(Consumer<DashboardData> callback) {
        Task<DashboardData> task = new Task<>() {
            @Override
            protected DashboardData call() throws Exception {
                LocalDate today = LocalDate.now();
                SalesSummary summary = dao.getSalesSummary(today, today);
                // List<HourlySales> hourly = dao.getHourlySales(today); // Removed
                List<ProductPerformance> topProducts = dao.getTopProducts(today, today, 10); // Changed limit to 10? Or
                                                                                             // just rename UI. But
                                                                                             // let's fetch more.
                List<com.malek.pos.models.Product> deadStock = dao.getDeadStock(3);

                return new DashboardData(summary, deadStock, topProducts);
            }
        };

        task.setOnSucceeded(e -> callback.accept(task.getValue()));
        task.setOnFailed(e -> task.getException().printStackTrace());

        new Thread(task).start();
    }

    public void fetchSalesSummary(LocalDate start, LocalDate end, Consumer<SalesSummary> callback) {
        Task<SalesSummary> task = new Task<>() {
            @Override
            protected SalesSummary call() {
                return dao.getSalesSummary(start, end);
            }
        };
        task.setOnSucceeded(e -> callback.accept(task.getValue()));
        new Thread(task).start();
    }

    public void fetchTopProducts(LocalDate start, LocalDate end, int limit,
            Consumer<List<ProductPerformance>> callback) {
        Task<List<ProductPerformance>> task = new Task<>() {
            @Override
            protected List<ProductPerformance> call() {
                return dao.getTopProducts(start, end, limit);
            }
        };
        task.setOnSucceeded(e -> callback.accept(task.getValue()));
        new Thread(task).start();
    }

    public void fetchPaymentBreakdown(LocalDate start, LocalDate end, Consumer<List<PaymentMethodSummary>> callback) {
        new Thread(() -> {
            List<PaymentMethodSummary> list = dao.getPaymentBreakdown(start, end);
            callback.accept(list);
        }).start();
    }

    public void fetchZReadReport(LocalDate date,
            Consumer<com.malek.pos.models.reporting.ReportingDTOs.ZReadReportDTO> callback) {
        new Thread(() -> {
            com.malek.pos.models.reporting.ReportingDTOs.ZReadReportDTO report = dao.getZReadReport(date);
            callback.accept(report);
        }).start();
    }

    public void fetchDetailedLaybyReport(LocalDate start, LocalDate end,
            Consumer<LaybyDetailedReport> callback) {
        Task<LaybyDetailedReport> task = new Task<>() {
            @Override
            protected LaybyDetailedReport call() {
                return laybyDAO.getDetailedLaybyReport(start, end);
            }
        };
        task.setOnSucceeded(e -> callback.accept(task.getValue()));
        new Thread(task).start();
    }

    // --- Wrapper for Dashboard ---
    public record DashboardData(SalesSummary summary, List<com.malek.pos.models.Product> deadStock,
            List<ProductPerformance> topProducts) {
    }
}
