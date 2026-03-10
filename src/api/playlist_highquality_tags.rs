/// 精品歌单标签
/// 对应 Node.js module/playlist_highquality_tags.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 精品歌单标签列表
    /// 对应 /playlist/highquality/tags
    pub async fn playlist_highquality_tags(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/playlist/highquality/tags",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
