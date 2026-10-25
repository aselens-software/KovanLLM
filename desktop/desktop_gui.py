import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import json
import io
import socket
import threading
import urllib.parse
import httpx
import qrcode
import uvicorn
from PIL import Image

from PyQt6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QPushButton, QComboBox, QSlider, QTextEdit, QTabWidget,
    QFrame, QMessageBox, QLineEdit, QCheckBox, QSpinBox, QScrollArea,
    QGraphicsDropShadowEffect
)
from PyQt6.QtCore import Qt, QTimer, pyqtSignal, QObject, QPoint, QByteArray
from PyQt6.QtGui import QPixmap, QImage, QFont, QColor, QIcon, QPainter
from PyQt6.QtSvg import QSvgRenderer

from server import app as api_app, current_config, KOVAN_STATION_PORT, unload_engine_model, set_active_tunnel_url, session_auth_token, get_pairing_pin
from tunnel import tunnel_manager

def get_fa_icon(name: str, color: str = "#D97706", size: int = 16) -> QIcon:
    svg_path = os.path.join(os.path.dirname(__file__), "assets", "icons", f"{name}.svg")
    if not os.path.exists(svg_path):
        return QIcon()
    try:
        with open(svg_path, "r", encoding="utf-8") as f:
            content = f.read()
        content = content.replace("currentColor", color)
        renderer = QSvgRenderer(QByteArray(content.encode("utf-8")))
        pixmap = QPixmap(size, size)
        pixmap.fill(QColor(0, 0, 0, 0))
        painter = QPainter(pixmap)
        renderer.render(painter)
        painter.end()
        return QIcon(pixmap)
    except Exception:
        return QIcon()

I18N = {
    "en": {
        "title": "KovanLLM",
        "subtitle": "Local Model Station & Mobile Pairing Hub",
        "station_port_badge": f"Station: {KOVAN_STATION_PORT}",
        "tab_pairing": "Mobile Pairing",
        "tab_engine": "Engine & Port",
        "tab_model": "Model & Hardware",
        "status_checking": "Checking local engine status...",
        "status_station_active": f"Station Active on Port {KOVAN_STATION_PORT}",
        "status_station_lan_only": f"Station Active (Local Wi-Fi Only) on Port {KOVAN_STATION_PORT}",
        "scan_qr_hint": "Scan with Phone Camera to Open KovanLLM Directly",
        "connect_title": "Connect from Android",
        "connect_desc": "Open KovanLLM on your phone or scan the QR code. Connect via local Wi-Fi or secure internet tunnel.",
        "lan_title": "Local Wi-Fi Network:",
        "tunnel_title": "Internet (Encrypted Invisible Tunnel):",
        "pin_title": "Station Pairing PIN:",
        "tunnel_starting": "Starting tunnel...",
        "tunnel_offline": "Offline (Local Wi-Fi Only)",
        "tunnel_retry": "Retry Tunnel",
        "engine_settings_title": "LLM Engine Connection",
        "engine_desc": "Configure the local engine (LM Studio, Ollama, llama.cpp) running on this machine.",
        "engine_port_label": "Engine Port (e.g. 58080, 1234, 11434):",
        "test_connection": "Test Connection",
        "models_found": "models detected",
        "hardware_title": "Hardware & Generation Parameters",
        "model_label": "Active Model:",
        "ctx_label": "Context Length (n_ctx):",
        "custom_ctx_hint": "Enter custom token size",
        "gpu_offload_label": "Graduated GPU Offload:",
        "cpu_threads_label": "CPU Threads:",
        "temp_label": "Temperature (Creativity):",
        "top_p_label": "Top P (Nucleus Sampling):",
        "max_tokens_label": "Max Output Tokens:",
        "unlimited": "Unlimited (-1)",
        "sys_role_enable": "Enable System Role (Optional)",
        "sys_prompt_label": "System Prompt Template:",
        "free_vram_btn": "Eject Model / Free GPU VRAM",
        "refresh_models": "Refresh Models",
        "preset_general": "General Assistant",
        "preset_coder": "Software Engineer",
        "preset_translator": "Language Specialist",
        "preset_summarizer": "Executive Summarizer",
        "apply_reload_btn": "Apply & Reload Model",
        "applying_model": "Applying parameters & reloading model...",
        "require_pin_label": "Require Security PIN for Pairing",
        "pin_optional_badge": "PIN: Disabled (Optional)",
        "pin_required_badge": "PIN: {pin} (Required)",
    },
    "tr": {
        "title": "KovanLLM",
        "subtitle": "Yerel Model İstasyonu & Mobil Eşleşme Merkezi",
        "station_port_badge": f"İstasyon: {KOVAN_STATION_PORT}",
        "tab_pairing": "Mobil Eşleştirme",
        "tab_engine": "Motor & Port",
        "tab_model": "Model & Donanım",
        "status_checking": "Yerel motor durumu denetleniyor...",
        "status_station_active": f"İstasyon Port {KOVAN_STATION_PORT} Üzerinde Aktif",
        "status_station_lan_only": f"İstasyon Aktif (Yalnızca Yerel Wi-Fi) • Port {KOVAN_STATION_PORT}",
        "scan_qr_hint": "Kameranızla Tarayın, KovanLLM Doğrudan Açılsın",
        "connect_title": "Android ile Bağlan",
        "connect_desc": "Telefondan QR kodu tarayın veya KovanLLM'i açın. İster yerel Wi-Fi, ister güvenli tünel ile bağlanın.",
        "lan_title": "Yerel Ağ (Ev Wi-Fi):",
        "tunnel_title": "Dış Ağ (Görünmez Şifreli Tünel):",
        "pin_title": "İstasyon Eşleşme PIN Kodu:",
        "tunnel_starting": "Tünel hazırlanıyor...",
        "tunnel_offline": "Çevrimdışı (Yalnızca Yerel Wi-Fi)",
        "tunnel_retry": "Tüneli Yenile",
        "engine_settings_title": "LLM Motoru Bağlantısı",
        "engine_desc": "Bu bilgisayarda çalışan yerel motoru (LM Studio, Ollama, llama.cpp) yapılandırın.",
        "engine_port_label": "Motor Portu (örn: 58080, 1234, 11434):",
        "test_connection": "Bağlantıyı Test Et",
        "models_found": "model tespit edildi",
        "hardware_title": "Donanım & Üretim Parametreleri",
        "model_label": "Aktif Model:",
        "ctx_label": "Bağlam Uzunluğu (Context Length):",
        "custom_ctx_hint": "Özel token sayısı girin",
        "gpu_offload_label": "Dereceli GPU Yük Aktarımı:",
        "cpu_threads_label": "CPU İş Parçacığı Sayısı:",
        "temp_label": "Sıcaklık (Yaratıcılık):",
        "top_p_label": "Top P (Örnekleme):",
        "max_tokens_label": "Maksimum Üretim Tokenı:",
        "unlimited": "Sınırsız (-1)",
        "sys_role_enable": "Sistem Rolü Kullan (İsteğe Bağlı)",
        "sys_prompt_label": "Sistem Rolü Metni:",
        "free_vram_btn": "Modeli Boşalt / GPU VRAM Temizle",
        "refresh_models": "Modelleri Yenile",
        "preset_general": "Genel Asistan",
        "preset_coder": "Yazılım Mühendisi",
        "preset_translator": "Dil Uzmanı",
        "preset_summarizer": "Özetleyici",
        "apply_reload_btn": "Uygula & Modeli Yeniden Başlat",
        "applying_model": "Parametreler uygulanıyor ve model yeniden başlatılıyor...",
        "require_pin_label": "Eşleşmede PIN Doğrulaması İste",
        "pin_optional_badge": "PIN: Devre Dışı (İsteğe Bağlı)",
        "pin_required_badge": "PIN: {pin} (Zorunlu)",
    }
}

class BridgeSignals(QObject):
    tunnel_ready = pyqtSignal(str)
    models_updated = pyqtSignal(list)
    engine_tested = pyqtSignal(bool, int, str)
    unload_finished = pyqtSignal(bool, str)
    reload_finished = pyqtSignal(bool, str)

signals = BridgeSignals()

def get_local_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

class KovanDesktopWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.lang = "en"
        self.is_dark_theme = True
        self._drag_pos = None

        # Frameless window setup
        self.setWindowTitle("KovanLLM")
        self.setWindowFlags(Qt.WindowType.FramelessWindowHint)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground, True)
        self.setFixedSize(800, 680)

        # Set Window Icon
        icon_path = os.path.join(os.path.dirname(__file__), "assets", "icon.png")
        if os.path.exists(icon_path):
            self.setWindowIcon(QIcon(icon_path))

        self.local_ip = get_local_ip()
        self.tunnel_url = ""
        self.models = []

        signals.tunnel_ready.connect(self.on_tunnel_ready)
        signals.models_updated.connect(self.on_models_updated)
        signals.engine_tested.connect(self.on_engine_tested)
        signals.unload_finished.connect(self.on_unload_finished)
        signals.reload_finished.connect(self.on_reload_finished)

        self.build_ui()
        self.apply_theme()

        self.update_qr()

        # Start FastAPI in background daemon thread
        threading.Thread(target=self.run_fastapi_server, daemon=True).start()

        # Start tunnel in background
        threading.Thread(target=self.start_tunnel_async, daemon=True).start()

        # Check engine & fetch models
        threading.Thread(target=self.fetch_models_async, daemon=True).start()

    def t(self, key: str) -> str:
        return I18N.get(self.lang, I18N["en"]).get(key, key)

    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton and event.position().y() < 55:
            self._drag_pos = event.globalPosition().toPoint() - self.frameGeometry().topLeft()
            event.accept()

    def mouseMoveEvent(self, event):
        if event.buttons() == Qt.MouseButton.LeftButton and self._drag_pos is not None:
            self.move(event.globalPosition().toPoint() - self._drag_pos)
            event.accept()

    def mouseReleaseEvent(self, event):
        self._drag_pos = None

    def update_header_logo(self):
        assets_dir = os.path.join(os.path.dirname(__file__), "assets")
        logo_name = "kovanllm_whitetext_horizental.png" if self.is_dark_theme else "kovanllm_horizental.png"
        logo_path = os.path.join(assets_dir, logo_name)
        if os.path.exists(logo_path):
            pixmap = QPixmap(logo_path)
            self.logo_label.setPixmap(pixmap.scaledToHeight(28, Qt.TransformationMode.SmoothTransformation))
        else:
            self.logo_label.setText(self.t("title"))
            self.logo_label.setFont(QFont("Arial", 16, QFont.Weight.Bold))

    def build_ui(self):
        # Outer wrapper for rounded border
        self.outer_container = QWidget()
        self.outer_container.setObjectName("outerContainer")
        self.setCentralWidget(self.outer_container)

        outer_layout = QVBoxLayout(self.outer_container)
        outer_layout.setContentsMargins(16, 14, 16, 14)
        outer_layout.setSpacing(10)

        # Custom Frameless Titlebar
        titlebar = QHBoxLayout()
        titlebar.setContentsMargins(0, 0, 0, 4)

        # App Horizontal Logo & Port Badge
        title_box = QHBoxLayout()
        title_box.setSpacing(10)
        self.logo_label = QLabel()
        self.update_header_logo()
        title_box.addWidget(self.logo_label)

        self.port_badge = QLabel(self.t("station_port_badge"))
        self.port_badge.setObjectName("portBadge")
        title_box.addWidget(self.port_badge)
        titlebar.addLayout(title_box)

        titlebar.addStretch()

        # Header controls
        # Theme Toggle
        self.theme_btn = QPushButton()
        self.theme_btn.setObjectName("controlBtn")
        self.theme_btn.setFixedSize(40, 32)
        self.theme_btn.setToolTip("Toggle Dark/Light Theme")
        self.theme_btn.setIcon(get_fa_icon("moon" if self.is_dark_theme else "sun", "#D97706", 16))
        self.theme_btn.clicked.connect(self.toggle_theme)
        titlebar.addWidget(self.theme_btn)

        # Language Switcher
        self.lang_btn = QPushButton(f" {self.lang.upper()}")
        self.lang_btn.setObjectName("controlBtn")
        self.lang_btn.setFixedSize(72, 32)
        self.lang_btn.setToolTip("Toggle Language (EN / TR)")
        self.lang_btn.setIcon(get_fa_icon("globe", "#D97706", 15))
        self.lang_btn.clicked.connect(self.toggle_language)
        titlebar.addWidget(self.lang_btn)

        # Window Controls (_ ▢ ✕)
        self.min_btn = QPushButton("─")
        self.min_btn.setObjectName("winBtnMin")
        self.min_btn.setFixedSize(36, 32)
        self.min_btn.clicked.connect(self.showMinimized)
        titlebar.addWidget(self.min_btn)

        self.close_btn = QPushButton("✕")
        self.close_btn.setObjectName("winBtnClose")
        self.close_btn.setFixedSize(36, 32)
        self.close_btn.clicked.connect(self.close)
        titlebar.addWidget(self.close_btn)

        outer_layout.addLayout(titlebar)

        # Tabs
        self.tabs = QTabWidget()
        self.tabs.setObjectName("mainTabs")
        self.pairing_tab = QWidget()
        self.engine_tab = QWidget()
        self.hardware_tab = QWidget()

        self.setup_pairing_tab()
        self.setup_engine_tab()
        self.setup_hardware_tab()

        self.tabs.addTab(self.pairing_tab, self.t("tab_pairing"))
        self.tabs.addTab(self.engine_tab, self.t("tab_engine"))
        self.tabs.addTab(self.hardware_tab, self.t("tab_model"))
        self.tabs.setTabIcon(0, get_fa_icon("mobile", "#D97706", 16))
        self.tabs.setTabIcon(1, get_fa_icon("sliders", "#D97706", 16))
        self.tabs.setTabIcon(2, get_fa_icon("microchip", "#D97706", 16))
        outer_layout.addWidget(self.tabs)

        # Status Bar
        self.status_bar = QLabel(self.t("status_checking"))
        self.status_bar.setObjectName("statusBar")
        outer_layout.addWidget(self.status_bar)

        # Footer
        self.footer_label = QLabel(
            "Created by <a href='https://aselens.com' style='color: #D97706; text-decoration: none; font-weight: 600;'>Aselens Software℠</a> • Released under MIT License on GitHub"
        )
        self.footer_label.setOpenExternalLinks(True)
        self.footer_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.footer_label.setStyleSheet("color: #64748B; font-size: 11px; margin-top: 2px;")
        outer_layout.addWidget(self.footer_label)

    def toggle_theme(self):
        self.is_dark_theme = not self.is_dark_theme
        self.theme_btn.setIcon(get_fa_icon("moon" if self.is_dark_theme else "sun", "#D97706", 16))
        current_config.theme = "dark" if self.is_dark_theme else "light"
        self.apply_theme()

    def toggle_language(self):
        self.lang = "tr" if self.lang == "en" else "en"
        self.lang_btn.setText(f" {self.lang.upper()}")
        self.lang_btn.setIcon(get_fa_icon("globe", "#D97706", 15))
        current_config.language = self.lang
        self.refresh_ui_text()

    def refresh_ui_text(self):
        self.update_header_logo()
        self.port_badge.setText(self.t("station_port_badge"))
        self.tabs.setTabText(0, self.t("tab_pairing"))
        self.tabs.setTabText(1, self.t("tab_engine"))
        self.tabs.setTabText(2, self.t("tab_model"))
        self.tabs.setTabIcon(0, get_fa_icon("mobile", "#D97706", 16))
        self.tabs.setTabIcon(1, get_fa_icon("sliders", "#D97706", 16))
        self.tabs.setTabIcon(2, get_fa_icon("microchip", "#D97706", 16))
        self.theme_btn.setIcon(get_fa_icon("moon" if self.is_dark_theme else "sun", "#D97706", 16))
        self.lang_btn.setIcon(get_fa_icon("globe", "#D97706", 15))
        self.qr_hint.setText(self.t("scan_qr_hint"))
        self.step_title.setText(self.t("connect_title"))
        self.desc.setText(self.t("connect_desc"))
        self.lan_label.setText(self.t("lan_title"))
        self.tunnel_title.setText(self.t("tunnel_title"))
        if hasattr(self, "pin_title"):
            self.pin_title.setText(self.t("pin_title"))
        if hasattr(self, "pin_toggle"):
            self.pin_toggle.setText(self.t("require_pin_label"))
        self.update_pin_badge_display()
        self.engine_title.setText(self.t("engine_settings_title"))
        self.engine_desc.setText(self.t("engine_desc"))
        self.port_label.setText(self.t("engine_port_label"))
        self.test_btn.setText(self.t("test_connection"))
        self.model_label.setText(self.t("model_label"))
        self.ctx_label.setText(self.t("ctx_label"))
        self.gpu_label.setText(self.t("gpu_offload_label"))
        self.cpu_label.setText(self.t("cpu_threads_label"))
        self.temp_label.setText(self.t("temp_label"))
        self.sys_role_check.setText(self.t("sys_role_enable"))
        self.free_vram_btn.setText(self.t("free_vram_btn"))
        self.apply_btn.setText(self.t("apply_reload_btn"))
        self.refresh_btn.setText(self.t("refresh_models"))
        if hasattr(self, "tunnel_retry_btn"):
            self.tunnel_retry_btn.setToolTip(self.t("tunnel_retry"))
        if not self.tunnel_url and hasattr(self, "tunnel_text"):
            if self.tunnel_text.text() != self.t("tunnel_starting"):
                self.tunnel_text.setText(self.t("tunnel_offline"))

    def apply_theme(self):
        self.update_header_logo()
        if self.is_dark_theme:
            bg = "#0B0D14"
            container_bg = "#121520"
            surface = "#1A1E2E"
            card_bg = "#161926"
            border = "#262B3D"
            text = "#F3F4F6"
            text_muted = "#9CA3AF"
            primary = "#D97706"
            primary_hover = "#B45309"
            accent_green = "#10B981"
        else:
            bg = "#E2E8F0"
            container_bg = "#FFFFFF"
            surface = "#F8FAFC"
            card_bg = "#F1F5F9"
            border = "#CBD5E1"
            text = "#0F172A"
            text_muted = "#64748B"
            primary = "#D97706"
            primary_hover = "#B45309"
            accent_green = "#059669"

        self.setStyleSheet(f"""
            QWidget#outerContainer {{
                background-color: {container_bg};
                border: 1px solid {border};
                border-radius: 14px;
            }}
            QWidget {{
                color: {text};
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            }}
            QLabel#portBadge {{
                background-color: {card_bg};
                color: {primary};
                padding: 4px 10px;
                border-radius: 6px;
                border: 1px solid {border};
                font-size: 11px;
                font-weight: bold;
            }}
            QPushButton#controlBtn {{
                background-color: {card_bg};
                color: {text_muted};
                border: 1px solid {border};
                border-radius: 6px;
                font-size: 12px;
                font-weight: bold;
                padding: 0px 6px;
                text-align: center;
            }}
            QPushButton#controlBtn:hover {{
                color: {text};
                background-color: {surface};
            }}
            QPushButton#winBtnMin {{
                background-color: {card_bg};
                color: {text_muted};
                border: 1px solid {border};
                border-radius: 6px;
                font-size: 13px;
                font-weight: bold;
                padding: 0px;
                text-align: center;
            }}
            QPushButton#winBtnMin:hover {{
                background-color: {surface};
                color: {text};
            }}
            QPushButton#winBtnClose {{
                background-color: {card_bg};
                color: {text_muted};
                border: 1px solid {border};
                border-radius: 6px;
                font-size: 13px;
                font-weight: bold;
                padding: 0px;
                text-align: center;
            }}
            QPushButton#winBtnClose:hover {{
                background-color: #EF4444;
                color: #FFFFFF;
                border-color: #EF4444;
            }}
            QTabWidget::pane {{
                border: 1px solid {border};
                background-color: {surface};
                border-radius: 10px;
            }}
            QTabBar::tab {{
                background: {card_bg};
                color: {text_muted};
                padding: 8px 18px;
                margin-right: 4px;
                border-top-left-radius: 8px;
                border-top-right-radius: 8px;
                font-weight: 600;
                font-size: 12px;
            }}
            QTabBar::tab:selected {{
                background: {surface};
                color: {primary};
                border-bottom: 2px solid {primary};
            }}
            QComboBox, QTextEdit, QLineEdit, QSpinBox {{
                background-color: {card_bg};
                border: 1px solid {border};
                border-radius: 8px;
                padding: 6px 10px;
                color: {text};
            }}
            QPushButton {{
                background-color: {primary};
                color: #FFFFFF;
                border-radius: 8px;
                padding: 8px 14px;
                font-weight: 600;
            }}
            QPushButton:hover {{
                background-color: {primary_hover};
            }}
            QSlider::groove:horizontal {{
                height: 6px;
                background: {border};
                border-radius: 3px;
            }}
            QSlider::sub-page:horizontal {{
                background: {primary};
                border-radius: 3px;
            }}
            QSlider::handle:horizontal {{
                background: #FFFFFF;
                width: 16px;
                margin-top: -5px;
                margin-bottom: -5px;
                border-radius: 8px;
            }}
            QLabel#statusBar {{
                color: {text_muted};
                font-size: 12px;
                margin-top: 4px;
            }}
        """)

    def setup_pairing_tab(self):
        layout = QHBoxLayout(self.pairing_tab)
        layout.setContentsMargins(20, 20, 20, 20)

        # Left QR
        left_box = QVBoxLayout()
        left_box.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.qr_label = QLabel()
        self.qr_label.setFixedSize(210, 210)
        self.qr_label.setStyleSheet("background-color: #FFFFFF; border-radius: 12px; padding: 8px;")
        self.qr_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        left_box.addWidget(self.qr_label)

        self.qr_hint = QLabel(self.t("scan_qr_hint"))
        self.qr_hint.setStyleSheet("color: #6B7280; font-size: 11px; margin-top: 6px; font-weight: 500;")
        self.qr_hint.setAlignment(Qt.AlignmentFlag.AlignCenter)
        left_box.addWidget(self.qr_hint)
        layout.addLayout(left_box)
        layout.addSpacing(16)

        # Right Connection info
        right_box = QVBoxLayout()
        self.step_title = QLabel(self.t("connect_title"))
        self.step_title.setFont(QFont("Arial", 16, QFont.Weight.Bold))
        right_box.addWidget(self.step_title)

        self.desc = QLabel(self.t("connect_desc"))
        self.desc.setStyleSheet("color: #9CA3AF; line-height: 140%; font-size: 12px; margin-top: 2px;")
        self.desc.setWordWrap(True)
        right_box.addWidget(self.desc)

        right_box.addSpacing(8)

        addr_box = QVBoxLayout()
        addr_box.setSpacing(5)

        self.lan_label = QLabel(self.t("lan_title"))
        self.lan_label.setStyleSheet("color: #6B7280; font-size: 11px; font-weight: 600;")
        addr_box.addWidget(self.lan_label)

        self.lan_text = QLabel(f"http://{self.local_ip}:{KOVAN_STATION_PORT}")
        self.lan_text.setStyleSheet("""
            background-color: rgba(16, 185, 129, 0.1); color: #10B981; padding: 7px 12px;
            border-radius: 6px; border: 1px solid rgba(16, 185, 129, 0.3); font-family: monospace; font-weight: 600;
        """)
        addr_box.addWidget(self.lan_text)

        self.tunnel_title = QLabel(self.t("tunnel_title"))
        self.tunnel_title.setStyleSheet("color: #6B7280; font-size: 11px; font-weight: 600; margin-top: 4px;")
        addr_box.addWidget(self.tunnel_title)

        tunnel_row = QHBoxLayout()
        tunnel_row.setSpacing(6)
        self.tunnel_text = QLabel(self.t("tunnel_starting"))
        self.tunnel_text.setStyleSheet("""
            background-color: rgba(217, 119, 6, 0.1); color: #D97706; padding: 7px 12px;
            border-radius: 6px; border: 1px solid rgba(217, 119, 6, 0.3); font-family: monospace; font-weight: 600;
        """)
        tunnel_row.addWidget(self.tunnel_text, stretch=1)

        self.tunnel_retry_btn = QPushButton()
        self.tunnel_retry_btn.setObjectName("controlBtn")
        self.tunnel_retry_btn.setFixedSize(36, 32)
        self.tunnel_retry_btn.setToolTip(self.t("tunnel_retry"))
        self.tunnel_retry_btn.setIcon(get_fa_icon("rotate", "#D97706", 14))
        self.tunnel_retry_btn.clicked.connect(self.retry_tunnel)
        tunnel_row.addWidget(self.tunnel_retry_btn)

        addr_box.addLayout(tunnel_row)

        self.pin_title = QLabel(self.t("pin_title"))
        self.pin_title.setStyleSheet("color: #6B7280; font-size: 11px; font-weight: 600; margin-top: 4px;")
        addr_box.addWidget(self.pin_title)

        self.pin_toggle = QCheckBox(self.t("require_pin_label"))
        self.pin_toggle.setChecked(current_config.pin_required)
        self.pin_toggle.setStyleSheet("font-size: 11px; color: #D97706; font-weight: 600; margin-bottom: 2px;")
        self.pin_toggle.toggled.connect(self.on_pin_toggle_changed)
        addr_box.addWidget(self.pin_toggle)

        self.pin_badge = QLabel()
        self.update_pin_badge_display()
        addr_box.addWidget(self.pin_badge)

        right_box.addLayout(addr_box)
        layout.addLayout(right_box)

    def update_pin_badge_display(self):
        if not hasattr(self, "pin_badge"):
            return
        if current_config.pin_required:
            pin_str = get_pairing_pin()
            formatted_pin = f"{pin_str[:3]} {pin_str[3:]}"
            self.pin_badge.setText(self.t("pin_required_badge").format(pin=formatted_pin))
            self.pin_badge.setStyleSheet("""
                background-color: rgba(217, 119, 6, 0.12); color: #D97706; padding: 7px 12px;
                border-radius: 6px; border: 1px solid rgba(217, 119, 6, 0.35); font-family: monospace; font-size: 13px; font-weight: bold;
            """)
        else:
            self.pin_badge.setText(self.t("pin_optional_badge"))
            self.pin_badge.setStyleSheet("""
                background-color: rgba(100, 116, 139, 0.12); color: #94A3B8; padding: 7px 12px;
                border-radius: 6px; border: 1px solid rgba(100, 116, 139, 0.3); font-family: monospace; font-size: 12px; font-weight: 600;
            """)

    def on_pin_toggle_changed(self, checked: bool):
        current_config.pin_required = checked
        self.update_pin_badge_display()
        self.update_qr()

    def setup_engine_tab(self):
        layout = QVBoxLayout(self.engine_tab)
        layout.setContentsMargins(20, 20, 20, 20)

        self.engine_title = QLabel(self.t("engine_settings_title"))
        self.engine_title.setFont(QFont("Arial", 15, QFont.Weight.Bold))
        layout.addWidget(self.engine_title)

        self.engine_desc = QLabel(self.t("engine_desc"))
        self.engine_desc.setStyleSheet("color: #9CA3AF; font-size: 12px;")
        layout.addWidget(self.engine_desc)
        layout.addSpacing(10)

        # Port Input
        self.port_label = QLabel(self.t("engine_port_label"))
        self.port_label.setStyleSheet("font-weight: 600;")
        layout.addWidget(self.port_label)

        port_row = QHBoxLayout()
        self.engine_port_input = QLineEdit(str(current_config.engine_port))
        self.engine_port_input.setPlaceholderText("58080")
        self.engine_port_input.setFixedWidth(140)
        port_row.addWidget(self.engine_port_input)

        self.test_btn = QPushButton(self.t("test_connection"))
        self.test_btn.setIcon(get_fa_icon("bolt", "#FFFFFF", 14))
        self.test_btn.clicked.connect(self.test_engine_connection)
        port_row.addWidget(self.test_btn)
        port_row.addStretch()
        layout.addLayout(port_row)

        # Presets Buttons
        presets_label = QLabel("Quick Presets:")
        presets_label.setStyleSheet("color: #6B7280; font-size: 11px; font-weight: bold; margin-top: 8px;")
        layout.addWidget(presets_label)

        presets_row = QHBoxLayout()
        presets = [("LM Studio (58080)", 58080), ("LM Studio (1234)", 1234), ("Ollama (11434)", 11434), ("llama.cpp (8080)", 8080)]
        for name, p in presets:
            btn = QPushButton(name)
            btn.setStyleSheet("""
                background-color: transparent; border: 1px solid #D97706; color: #D97706;
                padding: 6px 12px; font-size: 11px; border-radius: 6px; font-weight: 600;
            """)
            btn.clicked.connect(lambda checked, port=p: self.set_engine_port(port))
            presets_row.addWidget(btn)
        presets_row.addStretch()
        layout.addLayout(presets_row)

        layout.addSpacing(14)
        self.engine_status_badge = QLabel("Engine status: Pending check")
        self.engine_status_badge.setStyleSheet("color: #9CA3AF; font-size: 13px;")
        layout.addWidget(self.engine_status_badge)

        layout.addStretch()

    def setup_hardware_tab(self):
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setStyleSheet("QScrollArea { border: none; background: transparent; }")

        inner_widget = QWidget()
        layout = QVBoxLayout(inner_widget)
        layout.setContentsMargins(14, 14, 14, 14)
        layout.setSpacing(12)

        # Model Selector
        self.model_label = QLabel(self.t("model_label"))
        self.model_label.setStyleSheet("font-weight: 600;")
        layout.addWidget(self.model_label)

        self.model_combo = QComboBox()
        self.model_combo.currentIndexChanged.connect(self.on_model_selected)
        layout.addWidget(self.model_combo)

        # Context Length with Custom option
        self.ctx_label = QLabel(self.t("ctx_label"))
        self.ctx_label.setStyleSheet("font-weight: 600;")
        layout.addWidget(self.ctx_label)

        ctx_row = QHBoxLayout()
        self.ctx_combo = QComboBox()
        self.ctx_combo.addItems(["2048", "4096", "8192", "16384", "32768", "65536", "Custom / Özel"])
        self.ctx_combo.setCurrentText(str(current_config.context_length) if str(current_config.context_length) in ["2048", "4096", "8192", "16384", "32768", "65536"] else "Custom / Özel")
        self.ctx_combo.currentIndexChanged.connect(self.on_ctx_combo_changed)
        ctx_row.addWidget(self.ctx_combo)

        self.custom_ctx_input = QSpinBox()
        self.custom_ctx_input.setRange(512, 524288)
        self.custom_ctx_input.setSingleStep(1024)
        self.custom_ctx_input.setValue(current_config.context_length)
        self.custom_ctx_input.setVisible(self.ctx_combo.currentText() == "Custom / Özel")
        self.custom_ctx_input.valueChanged.connect(lambda v: setattr(current_config, "context_length", v))
        ctx_row.addWidget(self.custom_ctx_input)
        layout.addLayout(ctx_row)

        # Graduated GPU Offload (Slider from 0 to 100%)
        gpu_header = QHBoxLayout()
        self.gpu_label = QLabel(self.t("gpu_offload_label"))
        self.gpu_label.setStyleSheet("font-weight: 600;")
        self.gpu_val_label = QLabel(f"{current_config.gpu_offload_percent}% (Full GPU)" if current_config.gpu_offload_percent == 100 else f"{current_config.gpu_offload_percent}%")
        self.gpu_val_label.setStyleSheet("color: #D97706; font-weight: 700;")
        gpu_header.addWidget(self.gpu_label)
        gpu_header.addStretch()
        gpu_header.addWidget(self.gpu_val_label)
        layout.addLayout(gpu_header)

        self.gpu_slider = QSlider(Qt.Orientation.Horizontal)
        self.gpu_slider.setRange(0, 100)
        self.gpu_slider.setValue(current_config.gpu_offload_percent)
        self.gpu_slider.valueChanged.connect(self.on_gpu_slider_changed)
        layout.addWidget(self.gpu_slider)

        # CPU Threads
        cpu_row = QHBoxLayout()
        self.cpu_label = QLabel(self.t("cpu_threads_label"))
        self.cpu_label.setStyleSheet("font-weight: 600;")
        cpu_row.addWidget(self.cpu_label)
        self.cpu_spin = QSpinBox()
        self.cpu_spin.setRange(1, max(1, os.cpu_count() or 16))
        self.cpu_spin.setValue(current_config.cpu_threads)
        self.cpu_spin.valueChanged.connect(lambda v: setattr(current_config, "cpu_threads", v))
        cpu_row.addWidget(self.cpu_spin)
        layout.addLayout(cpu_row)

        # Temperature
        temp_header = QHBoxLayout()
        self.temp_label = QLabel(self.t("temp_label"))
        self.temp_label.setStyleSheet("font-weight: 600;")
        self.temp_val_label = QLabel(f"{current_config.temperature:.2f}")
        self.temp_val_label.setStyleSheet("color: #D97706; font-weight: 700;")
        temp_header.addWidget(self.temp_label)
        temp_header.addStretch()
        temp_header.addWidget(self.temp_val_label)
        layout.addLayout(temp_header)

        self.temp_slider = QSlider(Qt.Orientation.Horizontal)
        self.temp_slider.setRange(0, 200)
        self.temp_slider.setValue(int(current_config.temperature * 100))
        self.temp_slider.valueChanged.connect(self.on_temp_changed)
        layout.addWidget(self.temp_slider)

        # System Role Toggle (Optional)
        self.sys_role_check = QCheckBox(self.t("sys_role_enable"))
        self.sys_role_check.setChecked(current_config.system_role_enabled)
        self.sys_role_check.toggled.connect(self.on_sys_role_toggled)
        layout.addWidget(self.sys_role_check)

        self.sys_prompt_edit = QTextEdit()
        self.sys_prompt_edit.setPlaceholderText("You are an expert AI assistant...")
        self.sys_prompt_edit.setMaximumHeight(65)
        self.sys_prompt_edit.setEnabled(current_config.system_role_enabled)
        self.sys_prompt_edit.textChanged.connect(lambda: setattr(current_config, "system_prompt", self.sys_prompt_edit.toPlainText()))
        layout.addWidget(self.sys_prompt_edit)

        # Action Buttons
        btn_row = QHBoxLayout()
        self.apply_btn = QPushButton(self.t("apply_reload_btn"))
        self.apply_btn.setIcon(get_fa_icon("bolt", "#FFFFFF", 14))
        self.apply_btn.setStyleSheet("""
            background-color: #D97706; color: #FFFFFF; font-weight: bold; border-radius: 8px; padding: 10px 16px; font-size: 13px;
        """)
        self.apply_btn.clicked.connect(self.apply_and_reload_model)
        btn_row.addWidget(self.apply_btn)

        self.free_vram_btn = QPushButton(self.t("free_vram_btn"))
        self.free_vram_btn.setIcon(get_fa_icon("snowflake", "#FFFFFF", 14))
        self.free_vram_btn.setStyleSheet("""
            background-color: #DC2626; color: #FFFFFF; font-weight: bold; border-radius: 8px; padding: 10px 15px; font-size: 13px;
        """)
        self.free_vram_btn.clicked.connect(self.eject_model)
        btn_row.addWidget(self.free_vram_btn)

        self.refresh_btn = QPushButton(self.t("refresh_models"))
        self.refresh_btn.setIcon(get_fa_icon("rotate", "#FFFFFF", 14))
        self.refresh_btn.setStyleSheet("""
            background-color: #374151; color: #FFFFFF; font-weight: bold; border-radius: 8px; padding: 10px 15px; font-size: 13px;
        """)
        self.refresh_btn.clicked.connect(lambda: threading.Thread(target=self.fetch_models_async, daemon=True).start())
        btn_row.addWidget(self.refresh_btn)
        layout.addLayout(btn_row)

        scroll.setWidget(inner_widget)
        outer_layout = QVBoxLayout(self.hardware_tab)
        outer_layout.setContentsMargins(0, 0, 0, 0)
        outer_layout.addWidget(scroll)

    def on_ctx_combo_changed(self, index: int):
        text = self.ctx_combo.currentText()
        if text == "Custom / Özel":
            self.custom_ctx_input.setVisible(True)
            current_config.context_length = self.custom_ctx_input.value()
        else:
            self.custom_ctx_input.setVisible(False)
            current_config.context_length = int(text)

    def on_gpu_slider_changed(self, val: int):
        current_config.gpu_offload_percent = val
        if val == 0:
            current_config.gpu_offload = "off"
            self.gpu_val_label.setText("0% (CPU Only)")
        elif val == 100:
            current_config.gpu_offload = "max"
            self.gpu_val_label.setText("100% (Full GPU)")
        else:
            current_config.gpu_offload = f"{val/100:.2f}"
            self.gpu_val_label.setText(f"{val}% (Partial)")

    def set_engine_port(self, port: int):
        self.engine_port_input.setText(str(port))
        current_config.engine_port = port
        self.test_engine_connection()

    def test_engine_connection(self):
        try:
            port = int(self.engine_port_input.text().strip())
            current_config.engine_port = port
        except ValueError:
            return

        def run_test():
            try:
                with httpx.Client(timeout=3.0) as client:
                    res = client.get(f"http://127.0.0.1:{port}/v1/models")
                    if res.status_code == 200:
                        data = res.json()
                        models = [m.get("id") for m in data.get("data", []) if m.get("id")]
                        signals.engine_tested.emit(True, len(models), "")
                        signals.models_updated.emit(models)
                        return
            except Exception as e:
                signals.engine_tested.emit(False, 0, str(e))

        threading.Thread(target=run_test, daemon=True).start()

    def on_engine_tested(self, online: bool, count: int, error: str):
        if online:
            self.engine_status_badge.setText(f"Online: {count} {self.t('models_found')}")
            self.engine_status_badge.setStyleSheet("color: #10B981; font-size: 13px; font-weight: bold;")
        else:
            self.engine_status_badge.setText(f"Offline (Port {current_config.engine_port}): {error}")
            self.engine_status_badge.setStyleSheet("color: #EF4444; font-size: 13px;")

    def on_model_selected(self, index: int):
        if index >= 0 and self.model_combo.currentText():
            current_config.active_model = self.model_combo.currentText()

    def on_temp_changed(self, value: int):
        temp = value / 100.0
        current_config.temperature = temp
        self.temp_val_label.setText(f"{temp:.2f}")

    def on_sys_role_toggled(self, checked: bool):
        current_config.system_role_enabled = checked
        self.sys_prompt_edit.setEnabled(checked)

    def eject_model(self):
        def run_unload():
            try:
                res = httpx.post("http://127.0.0.1:27615/api/engine/unload", timeout=5.0)
                data = res.json()
                signals.unload_finished.emit(data.get("success", True), data.get("message", "Model unloaded."))
            except Exception as e:
                signals.unload_finished.emit(False, str(e))

        threading.Thread(target=run_unload, daemon=True).start()

    def on_unload_finished(self, success: bool, msg: str):
        QMessageBox.information(self, "GPU VRAM", msg)

    def apply_and_reload_model(self):
        self.apply_btn.setEnabled(False)
        self.status_bar.setText(self.t("applying_model"))
        self.status_bar.setStyleSheet("color: #F59E0B; font-size: 12px; margin-top: 4px;")

        payload = {
            "model": current_config.active_model,
            "context_length": current_config.context_length,
            "gpu_offload": current_config.gpu_offload
        }

        def run_reload():
            try:
                with httpx.Client(timeout=120.0) as client:
                    res = client.post(f"http://127.0.0.1:{KOVAN_STATION_PORT}/api/engine/apply-and-reload", json=payload)
                    data = res.json()
                    signals.reload_finished.emit(data.get("success", False), data.get("message", "Model reloaded."))
            except Exception as e:
                signals.reload_finished.emit(False, str(e))

        threading.Thread(target=run_reload, daemon=True).start()

    def on_reload_finished(self, success: bool, msg: str):
        self.apply_btn.setEnabled(True)
        if success:
            self.status_bar.setText(f"{msg}")
            self.status_bar.setStyleSheet("color: #10B981; font-size: 12px; margin-top: 4px;")
        else:
            self.status_bar.setText(f"Reload Error: {msg}")
            self.status_bar.setStyleSheet("color: #EF4444; font-size: 12px; margin-top: 4px;")
        QMessageBox.information(self, "KovanLLM Engine", msg)

    def update_qr(self):
        active_url = self.tunnel_url or f"http://{self.local_ip}:{KOVAN_STATION_PORT}"
        # Direct Deep Link URI scheme: kovanllm://pair?url=...&port=27615
        encoded_url = urllib.parse.quote(active_url, safe="")
        if current_config.pin_required:
            deep_link = f"kovanllm://pair?url={encoded_url}&port={KOVAN_STATION_PORT}&token={session_auth_token}"
        else:
            deep_link = f"kovanllm://pair?url={encoded_url}&port={KOVAN_STATION_PORT}"

        qr = qrcode.QRCode(box_size=4, border=1)
        qr.add_data(deep_link)
        qr.make(fit=True)
        img = qr.make_image(fill_color="black", back_color="white")

        buffer = io.BytesIO()
        img.save(buffer, format="PNG")
        qimage = QImage.fromData(buffer.getvalue())
        pixmap = QPixmap.fromImage(qimage).scaled(190, 190, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
        self.qr_label.setPixmap(pixmap)

    def run_fastapi_server(self):
        uvicorn.run(api_app, host="0.0.0.0", port=KOVAN_STATION_PORT, log_level="warning")

    def start_tunnel_async(self):
        def on_ready(url):
            signals.tunnel_ready.emit(url)

        tunnel_manager.start(on_url_ready=on_ready)

    def retry_tunnel(self):
        self.tunnel_text.setText(self.t("tunnel_starting"))
        self.tunnel_text.setStyleSheet("""
            background-color: rgba(217, 119, 6, 0.1); color: #D97706; padding: 7px 12px;
            border-radius: 6px; border: 1px solid rgba(217, 119, 6, 0.3); font-family: monospace; font-weight: 600;
        """)
        self.tunnel_retry_btn.setEnabled(False)
        def bg():
            self.start_tunnel_async()
            QTimer.singleShot(2000, lambda: self.tunnel_retry_btn.setEnabled(True))
        threading.Thread(target=bg, daemon=True).start()

    def on_tunnel_ready(self, url: str):
        self.tunnel_url = url
        set_active_tunnel_url(url)
        if url:
            self.tunnel_text.setText(url)
            self.tunnel_text.setStyleSheet("""
                background-color: rgba(217, 119, 6, 0.1); color: #D97706; padding: 7px 12px;
                border-radius: 6px; border: 1px solid rgba(217, 119, 6, 0.3); font-family: monospace; font-weight: 600;
            """)
            self.status_bar.setText(self.t("status_station_active"))
            self.status_bar.setStyleSheet("color: #10B981; font-size: 12px; margin-top: 4px;")
        else:
            self.tunnel_text.setText(self.t("tunnel_offline"))
            self.tunnel_text.setStyleSheet("""
                background-color: rgba(239, 68, 68, 0.1); color: #EF4444; padding: 7px 12px;
                border-radius: 6px; border: 1px solid rgba(239, 68, 68, 0.3); font-family: monospace; font-weight: 600;
            """)
            self.status_bar.setText(self.t("status_station_lan_only"))
            self.status_bar.setStyleSheet("color: #F59E0B; font-size: 12px; margin-top: 4px;")
        self.update_qr()

    def fetch_models_async(self):
        try:
            with httpx.Client(timeout=3.0) as client:
                res = client.get(f"http://127.0.0.1:{current_config.engine_port}/v1/models")
                if res.status_code == 200:
                    data = res.json()
                    model_ids = [m["id"] for m in data.get("data", []) if "id" in m]
                    signals.models_updated.emit(model_ids)
                    return
        except Exception:
            pass
        signals.models_updated.emit([])

    def on_models_updated(self, model_list):
        self.model_combo.clear()
        if model_list:
            self.model_combo.addItems(model_list)
            if not current_config.active_model and model_list:
                current_config.active_model = model_list[0]
            self.status_bar.setText(f"Station Port {KOVAN_STATION_PORT} Active • {len(model_list)} models available")
            self.status_bar.setStyleSheet("color: #10B981; font-size: 12px; margin-top: 4px;")
        else:
            self.model_combo.addItem("qwen2.5-3b-instruct-abliterated")
            self.model_combo.addItem("meta-llama-3.1-8b-instruct-abliterated")

def main():
    if sys.platform == "win32":
        try:
            import ctypes
            myappid = "com.aselens.kovanllm.station.1.1"
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID(myappid)
        except Exception:
            pass

    app = QApplication(sys.argv)
    app.setApplicationName("KovanLLM")
    app.setApplicationDisplayName("KovanLLM")
    app.setDesktopFileName("kovanllm.desktop")

    icon_path = os.path.join(os.path.dirname(__file__), "assets", "icon.png")
    if os.path.exists(icon_path):
        app.setWindowIcon(QIcon(icon_path))

    window = KovanDesktopWindow()
    window.setWindowTitle("KovanLLM")
    window.show()
    sys.exit(app.exec())

if __name__ == "__main__":
    main()
