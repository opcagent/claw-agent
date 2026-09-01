package com.claw.agent.controller.chat;

import com.claw.agent.common.BizException;
import com.claw.agent.common.ReactiveSupport;
import com.claw.agent.common.Result;
import com.claw.agent.common.ResultCode;
import com.claw.agent.config.infra.ClawProperties;
import com.claw.agent.config.infra.TraceFilter;
import com.claw.agent.security.SecurityUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传控制器：聊天附件（图片/文档）落盘。
 * <p>
 * 方法级鉴权：任何已登录用户可上传（目录按用户名隔离）；
 * 存储结构：上传根目录/用户名/uuid_原始文件名（按用户隔离，防重名覆盖）；
 * 返回的 fileName 由前端随聊天请求带回，AgentService 组装多模态消息时读取。
 */
@Slf4j
@Tag(name = "文件上传", description = "图片/文件上传与多模态消息")
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UploadController {

    private final ClawProperties properties;

    /** 上传单个文件，返回存储名（聊天请求中引用） */
    @Operation(summary = "上传文件", description = "上传单个文件，返回存储名用于聊天请求中引用")
    @PostMapping
    public Mono<Result<Map<String, String>>> upload(@RequestPart("file") FilePart filePart) {
        // 不走 ReactiveSupport（返回类型/错误映射特殊）：从上下文取 traceId 自行桥接进 MDC，上传日志带链路 ID
        return Mono.deferContextual(ctxView -> {
            String traceId = ctxView.getOrDefault(TraceFilter.CONTEXT_KEY, null);
            return SecurityUtil.currentUser()
                    .flatMap(user -> Mono.fromCallable(() -> {
                        ReactiveSupport.putTrace(traceId);
                        try {
                            String original = filePart.filename();
                            // 安全规约：扩展名白名单 + 大小上限（超大缓冲另有 spring.webflux.codec.max-in-memory-size 兜底）
                            checkFileAllowed(original, filePart.headers().getContentLength());
                            // 用户目录隔离：每个用户的附件互不可见
                            Path userDir = Paths.get(properties.getUpload().getDir(),
                                    user.getUsername()).toAbsolutePath().normalize();
                            java.nio.file.Files.createDirectories(userDir);
                            String storedName = UUID.randomUUID().toString().replace("-", "") + "_" + original;
                            Path target = userDir.resolve(storedName).normalize();
                            if (!target.startsWith(userDir)) {
                                throw new BizException(ResultCode.PARAM_ERROR, "非法文件名");
                            }
                            // 落盘（超过内存缓冲上限会抛 DataBufferLimitException，由全局异常处理）；
                            // 完成回调线程不定，成功日志单独桥接一次 MDC 再清理，避免线程池串号
                            return filePart.transferTo(target)
                                    .doOnSuccess(v -> {
                                        ReactiveSupport.putTrace(traceId);
                                        try {
                                            log.info("文件上传成功: user={}, file={}", user.getUsername(), storedName);
                                        } finally {
                                            MDC.remove(TraceFilter.MDC_KEY);
                                        }
                                    })
                                    .thenReturn(Map.of("fileName", storedName, "originalName", original));
                        } finally {
                            MDC.remove(TraceFilter.MDC_KEY);
                        }
                    }).flatMap(mono -> mono).subscribeOn(Schedulers.boundedElastic()))
                    .map(Result::ok)
                    .onErrorMap(e -> {
                        if (e instanceof BizException biz) {
                            return biz;
                        }
                        log.error("文件上传失败", e);
                        return new BizException(ResultCode.UPLOAD_ERROR, "上传失败：" + e.getMessage());
                    });
        });
    }

    /**
     * 上传前置校验：扩展名白名单 + 大小上限。
     * <p>
     * multipart 请求的 Content-Length 可能缺失（-1），此时无法预检大小，
     * 由 spring.webflux.codec.max-in-memory-size 在缓冲层兜底拦截。
     *
     * @param filename      原始文件名
     * @param contentLength 请求声明的文件大小（可能为 -1）
     */
    private void checkFileAllowed(String filename, long contentLength) {
        int dot = filename.lastIndexOf('.');
        String ext = dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
        if (!properties.getUpload().getAllowedExtensions().contains(ext)) {
            throw new BizException(ResultCode.PARAM_ERROR, "不支持的文件类型，仅允许图片与常用文档格式");
        }
        long maxBytes = (long) properties.getUpload().getMaxSizeMb() * 1024 * 1024;
        if (contentLength > maxBytes) {
            throw new BizException(ResultCode.PARAM_ERROR, "文件超过大小上限");
        }
    }

    /**
     * 下载/预览已上传的附件（图片返回 inline 可预览，文档返回 attachment 触发下载）。
     * <p>
     * 安全：仅允许访问当前用户目录下的文件，禁止路径穿越。
     *
     * @param fileName 存储文件名（UUID_原始名）
     * @return 文件流响应
     */
    @Operation(summary = "下载文件", description = "根据文件名下载已上传的文件")
    @GetMapping("/download")
    public Mono<ResponseEntity<Resource>> download(@RequestParam String fileName) {
        return SecurityUtil.currentUser()
                .map(user -> {
                    // 安全校验：禁止路径穿越
                    if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                        throw new BizException(ResultCode.PARAM_ERROR, "非法文件名");
                    }
                    Path userDir = Paths.get(properties.getUpload().getDir(), user.getUsername()).toAbsolutePath().normalize();
                    Path target = userDir.resolve(fileName).normalize();
                    if (!target.startsWith(userDir)) {
                        throw new BizException(ResultCode.PARAM_ERROR, "非法文件路径");
                    }
                    if (!java.nio.file.Files.exists(target)) {
                        throw new BizException(ResultCode.NOT_FOUND, "文件不存在");
                    }
                    // 根据扩展名推断 MIME 类型
                    String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";
                    MediaType mediaType = switch (ext) {
                        case "png" -> MediaType.IMAGE_PNG;
                        case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
                        case "gif" -> MediaType.IMAGE_GIF;
                        case "webp" -> MediaType.parseMediaType("image/webp");
                        case "bmp" -> MediaType.parseMediaType("image/bmp");
                        case "pdf" -> MediaType.APPLICATION_PDF;
                        default -> MediaType.APPLICATION_OCTET_STREAM;
                    };
                    boolean isImage = mediaType.getType().equals("image");
                    String disposition = isImage ? "inline" : ("attachment; filename=\"" + fileName.substring(fileName.indexOf('_') + 1) + "\"");
                    Resource resource = new FileSystemResource(target);
                    return ResponseEntity.ok()
                            .contentType(mediaType)
                            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                            .body(resource);
                })
                .switchIfEmpty(Mono.error(new BizException(ResultCode.UNAUTHORIZED, "未登录")));
    }
}
