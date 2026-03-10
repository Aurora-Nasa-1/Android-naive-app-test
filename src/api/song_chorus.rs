/// 副歌时间
/// 对应 Node.js module/song_chorus.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 副歌时间
    /// 对应 /song/chorus
    pub async fn song_chorus(&self, query: &Query) -> Result<ApiResponse> {
        let id = query.get_or("id", "0");
        let data = json!({
            "ids": format!("[{}]", id),
        });
        self.request("/api/song/chorus", data, query.to_option(CryptoType::default()))
            .await
    }
}
