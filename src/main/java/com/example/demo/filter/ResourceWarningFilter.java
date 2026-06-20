package com.example.demo.filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Locale;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * HTML 응답에 시스템 자원 사용률 경고 배너를 자동으로 삽입하는 필터입니다.
 */
public class ResourceWarningFilter implements Filter {
    public static final String THRESHOLD_INIT_PARAM = "resourceWarningThresholdPercent";

    private static final double DEFAULT_RESOURCE_WARNING_THRESHOLD = 90.0d;
    private static final DecimalFormat RESOURCE_USAGE_FORMAT = new DecimalFormat("0.0");

    private double resourceWarningThreshold = DEFAULT_RESOURCE_WARNING_THRESHOLD;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        resourceWarningThreshold = DEFAULT_RESOURCE_WARNING_THRESHOLD;
        if (filterConfig == null) {
            return;
        }

        String configuredThreshold = filterConfig.getInitParameter(THRESHOLD_INIT_PARAM);
        if (configuredThreshold == null || configuredThreshold.trim().length() == 0) {
            return;
        }

        try {
            double parsedThreshold = Double.parseDouble(configuredThreshold.trim());
            if (parsedThreshold < 0.0d || parsedThreshold > 100.0d) {
                throw new ServletException(THRESHOLD_INIT_PARAM + " 값은 0 이상 100 이하의 숫자여야 합니다.");
            }
            resourceWarningThreshold = parsedThreshold;
        } catch (NumberFormatException numberFormatException) {
            throw new ServletException(THRESHOLD_INIT_PARAM + " 값은 숫자여야 합니다.", numberFormatException);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (!isHtmlRequest(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletResponse httpResponse = (HttpServletResponse) response;
        ResourceWarningResponseWrapper responseWrapper = new ResourceWarningResponseWrapper(httpResponse);
        chain.doFilter(request, responseWrapper);

        String body = responseWrapper.getCapturedResponse();
        String contentType = responseWrapper.getContentType();
        ResourceUsageStatus usageStatus = getResourceUsageStatus();

        if (isHtmlResponse(contentType) && usageStatus.isWarning(resourceWarningThreshold)) {
            body = insertWarningBanner(body, usageStatus, resourceWarningThreshold);
        }

        byte[] responseBody = body.getBytes(responseWrapper.getCharset());
        httpResponse.setContentLength(responseBody.length);
        httpResponse.getOutputStream().write(responseBody);
    }

    @Override
    public void destroy() {
    }

    private static boolean isHtmlRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri == null
            || requestUri.endsWith("/")
            || requestUri.endsWith(".do")
            || requestUri.endsWith(".jsp")
            || requestUri.endsWith(".html")
            || requestUri.endsWith(".htm");
    }

    private static boolean isHtmlResponse(String contentType) {
        return contentType == null || contentType.toLowerCase(Locale.ROOT).contains("text/html");
    }

    private static ResourceUsageStatus getResourceUsageStatus() {
        double highestUsagePercent = -1.0d;
        String highestUsageName = "";

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        if (heapUsage != null && heapUsage.getMax() > 0) {
            double heapUsagePercent = ((double) heapUsage.getUsed() / (double) heapUsage.getMax()) * 100.0d;
            highestUsagePercent = heapUsagePercent;
            highestUsageName = "JVM 힙 메모리";
        }

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
            long totalPhysicalMemory = sunOsBean.getTotalMemorySize();
            long freePhysicalMemory = sunOsBean.getFreeMemorySize();
            if (totalPhysicalMemory > 0) {
                double systemMemoryUsagePercent = ((double) (totalPhysicalMemory - freePhysicalMemory)
                    / (double) totalPhysicalMemory) * 100.0d;
                if (systemMemoryUsagePercent > highestUsagePercent) {
                    highestUsagePercent = systemMemoryUsagePercent;
                    highestUsageName = "시스템 메모리";
                }
            }
        }

        double systemCpuUsagePercent = getSystemCpuUsagePercent(osBean);
        if (systemCpuUsagePercent > highestUsagePercent) {
            highestUsagePercent = systemCpuUsagePercent;
            highestUsageName = "시스템 CPU";
        }

        return new ResourceUsageStatus(highestUsageName, highestUsagePercent);
    }

    private static double getSystemCpuUsagePercent(OperatingSystemMXBean osBean) {
        // CPU 사용률은 WAS/JDK 버전에 따라 제공 여부가 달라 리플렉션으로 안전하게 확인합니다.
        try {
            Method systemCpuLoadMethod = osBean.getClass().getMethod("getCpuLoad");
            Object systemCpuLoadValue = systemCpuLoadMethod.invoke(osBean);
            if (systemCpuLoadValue instanceof Number) {
                double systemCpuLoad = ((Number) systemCpuLoadValue).doubleValue();
                if (systemCpuLoad >= 0.0d) {
                    return systemCpuLoad * 100.0d;
                }
            }
        } catch (Exception cpuLoadException) {
            double systemLoadAverage = osBean.getSystemLoadAverage();
            int availableProcessors = osBean.getAvailableProcessors();
            if (systemLoadAverage >= 0.0d && availableProcessors > 0) {
                return Math.min((systemLoadAverage / (double) availableProcessors) * 100.0d, 100.0d);
            }
        }
        return -1.0d;
    }

    private static String insertWarningBanner(String body, ResourceUsageStatus usageStatus, double warningThreshold) {
        String banner = buildWarningBanner(usageStatus, warningThreshold);
        String lowerBody = body.toLowerCase(Locale.ROOT);
        int bodyStartIndex = lowerBody.indexOf("<body");
        if (bodyStartIndex < 0) {
            return banner + body;
        }

        int bodyTagEndIndex = lowerBody.indexOf(">", bodyStartIndex);
        if (bodyTagEndIndex < 0) {
            return banner + body;
        }

        return body.substring(0, bodyTagEndIndex + 1) + banner + body.substring(bodyTagEndIndex + 1);
    }

    private static String buildWarningBanner(ResourceUsageStatus usageStatus, double warningThreshold) {
        String usagePercent = RESOURCE_USAGE_FORMAT.format(usageStatus.getUsagePercent());
        String usageName = escapeHtml(usageStatus.getUsageName());
        String thresholdPercent = RESOURCE_USAGE_FORMAT.format(warningThreshold);
        return "<style type=\"text/css\">"
            + ".resource-warning{margin:12px 0;padding:14px 16px;border:1px solid #d97706;"
            + "border-left:6px solid #d97706;border-radius:4px;background:#fff7ed;color:#7c2d12;"
            + "font-weight:bold;line-height:1.5;}"
            + "</style>"
            + "<div class=\"resource-warning\" role=\"alert\">"
            + "현재 " + usageName + " 사용률이 " + usagePercent + "%입니다. "
            + "JVM 힙/시스템 메모리/시스템 CPU 중 하나가 " + thresholdPercent
            + "% 이상 사용 중이므로 사이트가 느릴 수 있습니다."
            + "</div>";
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static class ResourceUsageStatus {
        private final String usageName;
        private final double usagePercent;

        ResourceUsageStatus(String usageName, double usagePercent) {
            this.usageName = usageName;
            this.usagePercent = usagePercent;
        }

        String getUsageName() {
            return usageName;
        }

        double getUsagePercent() {
            return usagePercent;
        }

        boolean isWarning(double warningThreshold) {
            return usagePercent >= warningThreshold;
        }
    }

    private static class ResourceWarningResponseWrapper extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        private ServletOutputStream outputStream;
        private PrintWriter writer;

        ResourceWarningResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(captureStream, getCharset()));
            }
            return writer;
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (outputStream == null) {
                outputStream = new CapturingServletOutputStream(captureStream);
            }
            return outputStream;
        }

        @Override
        public void setContentLength(int length) {
            // 배너 삽입 후 길이가 바뀔 수 있으므로 원본 Content-Length는 전달하지 않습니다.
        }

        @Override
        public void setContentLengthLong(long length) {
            // 배너 삽입 후 길이가 바뀔 수 있으므로 원본 Content-Length는 전달하지 않습니다.
        }

        String getCapturedResponse() throws IOException {
            if (writer != null) {
                writer.flush();
            }
            if (outputStream != null) {
                outputStream.flush();
            }
            return captureStream.toString(getCharset());
        }

        Charset getCharset() {
            String characterEncoding = getCharacterEncoding();
            if (characterEncoding == null || characterEncoding.trim().isEmpty()) {
                return StandardCharsets.UTF_8;
            }
            return Charset.forName(characterEncoding);
        }
    }

    private static class CapturingServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream captureStream;

        CapturingServletOutputStream(ByteArrayOutputStream captureStream) {
            this.captureStream = captureStream;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // 동기 방식으로만 캡처하므로 별도 처리하지 않습니다.
        }

        @Override
        public void write(int value) {
            captureStream.write(value);
        }
    }
}
