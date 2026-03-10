/// 精选电台分类
/// 对应 Node.js module/dj_recommend_type.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 精选电台分类
    /// 对应 /dj/recommend/type
    pub async fn dj_recommend_type(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "cateId": query.get_or("type", "0")
        });
        self.request(
            "/api/djradio/recommend",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
