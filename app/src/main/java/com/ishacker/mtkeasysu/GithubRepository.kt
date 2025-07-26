package com.ishacker.mtkeasysu

import retrofit2.Call
import retrofit2.http.GET

interface GithubRepository {
    @GET("/repos/IsHacker003/mtk-easy-su/releases/latest")
    fun getLatestRelease(): Call<GithubRelease>
}
