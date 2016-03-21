package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import java.util.*

fun ApplicationCall.basicAuthenticationCredentials(): UserPasswordCredential? = request.basicAuthenticationCredentials()
fun ApplicationRequest.basicAuthenticationCredentials(): UserPasswordCredential? {
    val parsed = parseAuthorizationHeader()
    when (parsed) {
        is HttpAuthHeader.Single -> {
            // here we can only use ISO 8859-1 character encoding because there is no character encoding specified as per RFC
            //     see http://greenbytes.de/tech/webdav/draft-reschke-basicauth-enc-latest.html
            //      http://tools.ietf.org/html/draft-ietf-httpauth-digest-15
            //      https://bugzilla.mozilla.org/show_bug.cgi?id=41489
            //      https://code.google.com/p/chromium/issues/detail?id=25790

            val userPass = Base64.getDecoder().decode(parsed.blob).toString(Charsets.ISO_8859_1)

            if (":" !in userPass) {
                return null
            }

            return UserPasswordCredential(userPass.substringBefore(":"), userPass.substringAfter(":"))
        }
        else -> return null
    }
}

fun PipelineContext<ApplicationCall>.basicAuthentication() {
    call.request.basicAuthenticationCredentials()?.let {
        call.authentication.addCredential(it)
    }
}

/**
 * The function constructs general basic auth flow: parse auth header, verify with [verifier] function
 * and send Unauthorized response with the specified [realm] in case of verification failure
 */
fun <P : Principal> PipelineContext<ApplicationCall>.basic(realm: String, verifier: ApplicationCall.(UserPasswordCredential) -> P?) {
    onFail {
        call.sendAuthenticationRequest(HttpAuthHeader.basicAuthChallenge(realm))
    }
    basicAuthentication()
    verifyWith(verifier)
}

fun AuthenticationProcedure.basicAuthentication(realm: String, validate: (UserPasswordCredential) -> Principal?) {
    authenticate { context ->
        val credentials = context.call.request.basicAuthenticationCredentials()
        val principal = credentials?.let(validate)
        if (principal != null) {
            context.principal(principal)
        } else {
            context.challenge {
                context.call.sendAuthenticationRequest(HttpAuthHeader.basicAuthChallenge(realm))
            }
        }
    }
}
