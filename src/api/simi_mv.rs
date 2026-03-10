/// 相似 MV
/// 对应 Node.js module/simi_mv.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 相似 MV
    /// 对应 /simi/mv
    pub async fn simi_mv(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "mvid": query.get_or("mvid", "0")
        });
        self.request(
            "/api/discovery/simiMV",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
