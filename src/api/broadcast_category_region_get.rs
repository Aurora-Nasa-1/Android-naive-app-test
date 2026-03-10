/// 广播电台 - 分类/地区信息
/// 对应 Node.js module/broadcast_category_region_get.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 广播电台 - 分类/地区信息
    /// 对应 /broadcast/category/region/get
    pub async fn broadcast_category_region_get(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/voice/broadcast/category/region/get",
            data,
            query.to_option(CryptoType::default()),
        )
        .await
    }
}
