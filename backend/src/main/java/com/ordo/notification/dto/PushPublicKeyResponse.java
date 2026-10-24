package com.ordo.notification.dto;

/** 구독할 때 applicationServerKey 로 쓰는 VAPID 공개키 (Base64url 87자) */
public record PushPublicKeyResponse(String publicKey) {
}
