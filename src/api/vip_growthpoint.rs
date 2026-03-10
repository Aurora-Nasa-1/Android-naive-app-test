/// 会员成长值
/// 对应 Node.js module/vip_growthpoint.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 会员成长值
    /// 对应 /vip/growthpoint
    pub async fn vip_growthpoint(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/vipnewcenter/app/level/growhpoint/basic",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
