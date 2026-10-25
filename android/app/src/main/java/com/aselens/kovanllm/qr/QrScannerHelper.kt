package com.aselens.kovanllm.qr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

data class ScannedPairResult(
    val url: String,
    val token: String? = null
)

object QrScannerHelper {
    /**
     * Starts the in-app Google Code Scanner camera to scan a KovanLLM station QR code.
     * Automatically extracts the station address and stationAuthToken from:
     * - "kovanllm://pair?url=http%3A%2F%2F192.168.1.100%3A27615&port=27615&token=..."
     * - "http://192.168.1.100:27615"
     * - "https://xxx.trycloudflare.com"
     */
    fun startScan(
        context: Context,
        onScanned: (result: ScannedPairResult) -> Unit,
        onError: (message: String) -> Unit = {}
    ) {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()

            val scanner = GmsBarcodeScanning.getClient(context, options)

            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val raw = barcode.rawValue ?: barcode.displayValue ?: ""
                    val pairResult = parseScannedPair(raw)
                    if (pairResult.url.isNotBlank()) {
                        onScanned(pairResult)
                    } else {
                        onError("Geçersiz veya boş QR kod.")
                    }
                }
                .addOnCanceledListener {
                    // User canceled scan
                }
                .addOnFailureListener { e ->
                    onError(e.localizedMessage ?: "Kamera tarayıcı hatası.")
                }
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Kamera başlatılamadı.")
        }
    }

    fun parseScannedPair(raw: String): ScannedPairResult {
        val trimmed = raw.trim()
        if (trimmed.startsWith("kovanllm://pair")) {
            try {
                val uri = Uri.parse(trimmed)
                val urlParam = uri.getQueryParameter("url")
                val tokenParam = uri.getQueryParameter("token")
                if (!urlParam.isNullOrBlank()) {
                    return ScannedPairResult(
                        url = urlParam.trim().trimEnd('/'),
                        token = tokenParam?.trim()?.takeIf { it.isNotBlank() }
                    )
                }
            } catch (e: Exception) {
                // fall through
            }
        }

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                val uri = Uri.parse(trimmed)
                val queryUrl = uri.getQueryParameter("url")
                val tokenParam = uri.getQueryParameter("token")
                if (!queryUrl.isNullOrBlank()) {
                    return ScannedPairResult(
                        url = queryUrl.trim().trimEnd('/'),
                        token = tokenParam?.trim()?.takeIf { it.isNotBlank() }
                    )
                }
            } catch (e: Exception) {
                // fall through
            }
            return ScannedPairResult(url = trimmed.trimEnd('/'), token = null)
        }

        return ScannedPairResult(url = trimmed, token = null)
    }

    fun parseScannedQr(raw: String): String = parseScannedPair(raw).url
}
