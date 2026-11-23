FROM rust:1.85-bookworm

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    unzip \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN rustup target add aarch64-linux-android x86_64-linux-android \
    && cargo install cargo-ndk --version 3.5.4 --locked

ARG NDK_VERSION=r27c
RUN curl -fsSL "https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux.zip" -o /tmp/ndk.zip \
    && unzip -q /tmp/ndk.zip -d /opt \
    && mv "/opt/android-ndk-${NDK_VERSION}" /opt/android-ndk \
    && rm -f /tmp/ndk.zip

ENV ANDROID_NDK_HOME=/opt/android-ndk
ENV RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384"
ENV CARGO_RESOLVER_INCOMPATIBLE_RUST_VERSIONS=fallback

WORKDIR /work
