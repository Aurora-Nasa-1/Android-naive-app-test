/// 更新歌单名
/// 对应 Node.js module/playlist_name_update.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 更新歌单名
    /// 对应 /playlist/name/update
    pub async fn playlist_name_update(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "id": query.get_or("id", ""),
            "name": query.get_or("name", "")
        });
        self.request(
            "/api/playlist/update/name",
            data,
            query.to_option(CryptoType::default()),
        )
        .await
    }
}
