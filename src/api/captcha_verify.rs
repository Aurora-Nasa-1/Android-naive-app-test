/// 校验验证码
/// 对应 Node.js module/captcha_verify.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 校验验证码
    /// 对应 /captcha/verify
    pub async fn captcha_verify(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "ctcode": query.get_or("ctcode", "86"),
            "cellphone": query.get("phone").unwrap_or(""),
            "captcha": query.get("captcha").unwrap_or("")
        });
        self.request(
            "/api/sms/captcha/verify",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
