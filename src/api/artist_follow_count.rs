/// 歌手粉丝数量
/// 对应 Node.js module/artist_follow_count.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 歌手粉丝数量
    /// 对应 /artist/follow/count
    pub async fn artist_follow_count(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "id": query.get_or("id", "0")
        });
        self.request(
            "/api/artist/follow/count/get",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
