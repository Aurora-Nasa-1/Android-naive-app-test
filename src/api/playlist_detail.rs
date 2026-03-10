/// 歌单详情
/// 对应 Node.js module/playlist_detail.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 歌单详情
    /// 对应 /playlist/detail
    pub async fn playlist_detail(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "id": query.get_or("id", "0"),
            "n": 100000,
            "s": query.get_or("s", "8").parse::<i64>().unwrap_or(8)
        });
        self.request(
            "/api/v6/playlist/detail",
            data,
            query.to_option(CryptoType::default()),
        )
        .await
    }
}
