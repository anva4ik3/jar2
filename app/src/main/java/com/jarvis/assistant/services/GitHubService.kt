package com.jarvis.assistant.services

class GitHubService {
    fun getRepositories(username: String, callback: (List<String>) -> Unit) {
        callback(emptyList())
    }
}
