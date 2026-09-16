package io.github.otajisan.youtubehandleidconverter.protection

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import tools.jackson.databind.ObjectMapper

/** Filter / EntryPoint から RFC 9457 ProblemDetail を直接書き出す */
internal fun HttpServletResponse.writeProblem(
    objectMapper: ObjectMapper,
    problem: ProblemDetail,
    headers: Map<String, String> = emptyMap(),
) {
    status = problem.status
    contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
    characterEncoding = Charsets.UTF_8.name()
    headers.forEach { (k, v) -> setHeader(k, v) }
    writer.write(objectMapper.writeValueAsString(problem))
    writer.flush()
}

internal fun isApiRequest(path: String) = path.startsWith("/api/")
