package com.nexaai.auth.domain;

/** How a session was established. Recorded so an auditor does not have to infer it. */
public enum TokenSource {
    /** Local registration plus password sign-in. */
    PASSWORD,
    /** "Continue with Google". */
    GOOGLE
}