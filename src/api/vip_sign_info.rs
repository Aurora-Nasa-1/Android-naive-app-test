/// 黑胶乐签签到信息
/// 对应 Node.js module/vip_sign_info.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 黑胶乐签签到信息
    /// 对应 /vip/sign/info
    pub async fn vip_sign_info(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/vipnewcenter/app/user/sign/info",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
