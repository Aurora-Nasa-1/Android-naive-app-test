/// 二维码 key 生成
/// 对应 Node.js module/login_qr_key.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 二维码 key 生成
    /// 对应 /login/qr/key
    pub async fn login_qr_key(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "type": 3
        });
        self.request(
            "/api/login/qrcode/unikey",
            data,
            query.to_option(CryptoType::default()),
        )
        .await
    }
}
