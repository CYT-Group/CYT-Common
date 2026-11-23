"""Shared configuration scope and Fernet format for test-to-production promotion."""
from base64 import urlsafe_b64encode
from hashlib import sha256

from cryptography.fernet import Fernet


CONFIG_TABLES = (
    'gateway_system', 'gateway_grant', 'gateway_system_apikey', 'gateway_module',
    'gateway_custom_api', 'gateway_op_category', 'gateway_op_order', 'gateway_environment',
    'gateway_secret', 'gateway_callback', 'gateway_wecom_form',
)


def fernet(key):
    if not key.strip():
        raise ValueError('凭据加密主密钥为空')
    return Fernet(urlsafe_b64encode(sha256(key.strip()).digest()))


