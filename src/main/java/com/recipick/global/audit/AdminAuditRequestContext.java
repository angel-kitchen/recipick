package com.recipick.global.audit;

import com.recipick.global.exception.AuthErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.SubjectType;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AdminAuditRequestContext {

    public AuthPrincipal requireAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BusinessException(AuthErrorCode.LOGIN_REQUIRED);
        }
        if (principal.type() != SubjectType.ADMIN) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        return principal;
    }

    public String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        // 전달 헤더를 직접 해석하지 않고, 신뢰 프록시 설정 이후의 Servlet 접속 주소만 사용한다.
        String address = attributes.getRequest().getRemoteAddr();
        if (address == null || address.length() > 45 || address.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = address.contains(":") ? parseIpv6(address) : parseIpv4(address);
            return bytes == null ? null : InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private byte[] parseIpv4(String address) {
        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("0|[1-9][0-9]{0,2}")) {
                return null;
            }
            int value = Integer.parseInt(parts[i]);
            if (value > 255) {
                return null;
            }
            bytes[i] = (byte) value;
        }
        return bytes;
    }

    private byte[] parseIpv6(String address) {
        if (!address.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        // IPv4가 포함된 IPv6도 숫자로 직접 해석하며 이름 조회를 사용하지 않는다.
        if (address.contains(".")) {
            int separator = address.lastIndexOf(':');
            byte[] ipv4 = parseIpv4(address.substring(separator + 1));
            if (ipv4 == null) {
                return null;
            }
            address = address.substring(0, separator + 1)
                    + Integer.toHexString((ipv4[0] & 255) * 256 + (ipv4[1] & 255)) + ":"
                    + Integer.toHexString((ipv4[2] & 255) * 256 + (ipv4[3] & 255));
        }
        int compression = address.indexOf("::");
        if (compression != address.lastIndexOf("::")) {
            return null;
        }
        String[] left;
        String[] right;
        if (compression >= 0) {
            String prefix = address.substring(0, compression);
            String suffix = address.substring(compression + 2);
            left = prefix.isEmpty() ? new String[0] : prefix.split(":", -1);
            right = suffix.isEmpty() ? new String[0] : suffix.split(":", -1);
            if (left.length + right.length >= 8) {
                return null;
            }
        } else {
            left = address.split(":", -1);
            right = new String[0];
            if (left.length != 8) {
                return null;
            }
        }
        byte[] bytes = new byte[16];
        return writeGroups(bytes, left, 0) && writeGroups(bytes, right, 8 - right.length) ? bytes : null;
    }

    private boolean writeGroups(byte[] bytes, String[] groups, int offset) {
        for (int i = 0; i < groups.length; i++) {
            if (!groups[i].matches("[0-9a-fA-F]{1,4}")) {
                return false;
            }
            int value = Integer.parseInt(groups[i], 16);
            bytes[(offset + i) * 2] = (byte) (value >>> 8);
            bytes[(offset + i) * 2 + 1] = (byte) value;
        }
        return true;
    }
}
