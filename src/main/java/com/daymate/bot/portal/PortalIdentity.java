package com.daymate.bot.portal;

import com.daymate.bot.entity.PortalUserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class PortalIdentity {
    private PortalIdentity() {
    }

    public static Long requireLinkedTelegramUserId(PortalUserEntity user) {
        if (user == null || user.getTelegramUserId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Connect your Telegram account before using this management area.");
        }
        return user.getTelegramUserId();
    }
}
