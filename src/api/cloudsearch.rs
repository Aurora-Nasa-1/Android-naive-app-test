/// 搜索
/// 对应 Node.js module/cloudsearch.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 搜索（云搜索）
    /// 对应 /cloudsearch
    pub async fn cloudsearch(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "s": query.get_or("keywords", ""),
            "type": query.get_or("type", "1").parse::<i64>().unwrap_or(1),
            "limit": query.get_or("limit", "30").parse::<i64>().unwrap_or(30),
            "offset": query.get_or("offset", "0").parse::<i64>().unwrap_or(0),
            "total": true
        });
        self.request(
            "/api/cloudsearch/pc",
            data,
            query.to_option(CryptoType::default()),
        )
        .await
    }
}
