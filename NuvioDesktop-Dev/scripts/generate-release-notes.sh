#!/usr/bin/env bash

set -euo pipefail

from_ref=""
to_ref="HEAD"
repository="${GITHUB_REPOSITORY:-}"
offline=false
repository_history=false
exclude_commits="${RELEASE_NOTES_EXCLUDE_COMMITS:-}"

usage() {
    echo "Usage: $0 --from <commit> [--to <commit>] [--repository <owner/repo>] [--repository-history] [--exclude <hashes>] [--offline]" >&2
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --from)
            from_ref="${2:-}"
            shift 2
            ;;
        --to)
            to_ref="${2:-}"
            shift 2
            ;;
        --repository)
            repository="${2:-}"
            shift 2
            ;;
        --repository-history)
            repository_history=true
            shift
            ;;
        --exclude)
            exclude_commits="${exclude_commits} ${2:-}"
            shift 2
            ;;
        --offline)
            offline=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage
            exit 1
            ;;
    esac
done

if [[ -z "$from_ref" ]]; then
    usage
    exit 1
fi

git cat-file -e "${from_ref}^{commit}" 2>/dev/null || {
    echo "Unknown starting commit: ${from_ref}" >&2
    exit 1
}
git cat-file -e "${to_ref}^{commit}" 2>/dev/null || {
    echo "Unknown ending commit: ${to_ref}" >&2
    exit 1
}

if [[ "$repository_history" == true && "$offline" == false ]]; then
    if [[ -z "$repository" ]]; then
        echo "Repository history mode requires --repository <owner/repo>." >&2
        exit 1
    fi
    if ! command -v gh >/dev/null 2>&1; then
        echo "Repository history mode requires the GitHub CLI unless --offline is used." >&2
        exit 1
    fi
    if ! command -v jq >/dev/null 2>&1; then
        echo "Repository history mode requires jq unless --offline is used." >&2
        exit 1
    fi
fi

is_excluded_hash() {
    local commit="$1"
    local excluded
    for excluded in ${exclude_commits//,/ }; do
        [[ -n "$excluded" ]] || continue
        if [[ "$commit" == "$excluded"* ]]; then
            return 0
        fi
    done
    return 1
}

is_release_note() {
    local subject_lower
    local version_bump_pattern='^(bump([[:space:]].*)?version|version[[:space:]]+bump)([[:space:]].*)?$'
    local cleanup_pattern='^cleanup([[:space:][:punct:]].*)?$'
    local conventional_noise_pattern='^(build|chore|ci|docs|style|test)(\([^)]*\))?:'
    subject_lower="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"

    [[ "$subject_lower" != *"[skip release notes]"* ]] || return 1
    [[ ! "$subject_lower" =~ $version_bump_pattern ]] || return 1
    [[ ! "$subject_lower" =~ $cleanup_pattern ]] || return 1
    [[ ! "$subject_lower" =~ $conventional_noise_pattern ]] || return 1
    return 0
}

resolve_username() {
    local commit="$1"
    local author_name="$2"
    local author_email="$3"
    local username=""

    if [[ "$author_email" =~ ^[0-9]+\+([^@]+)@users\.noreply\.github\.com$ ]]; then
        username="${BASH_REMATCH[1]}"
    elif [[ "$author_email" =~ ^([^@]+)@users\.noreply\.github\.com$ ]]; then
        username="${BASH_REMATCH[1]}"
    elif [[ "$offline" == false && -n "$repository" && -n "${GH_TOKEN:-}" ]] && command -v gh >/dev/null 2>&1; then
        username="$(gh api "repos/${repository}/commits/${commit}" --jq '.author.login // empty' 2>/dev/null || true)"
        if [[ -z "$username" ]]; then
            username="$(
                gh api \
                    -H 'Accept: application/vnd.github+json' \
                    "repos/${repository}/commits/${commit}/pulls" \
                    --jq '.[0].user.login // empty' \
                    2>/dev/null \
                    || true
            )"
        fi
    fi

    if [[ -z "$username" ]]; then
        username="$(printf '%s' "$author_name" | tr -cd '[:alnum:]_-')"
    fi
    printf '%s' "${username:-unknown}"
}

resolve_pull_request() {
    local commit="$1"
    local response

    response="$(
        gh api \
            -H 'Accept: application/vnd.github+json' \
            "repos/${repository}/commits/${commit}/pulls"
    )" || {
        echo "Could not resolve pull requests for ${commit} in ${repository}." >&2
        return 1
    }

    printf '%s' "$response" \
        | jq -r --arg repository "$repository" '
            map(select(.base.repo.full_name == $repository and .merged_at != null))
            | sort_by(.merged_at)
            | last
            | if . == null then
                empty
              else
                [
                    (.number | tostring),
                    (.title | gsub("[\t\r\n]+"; " ")),
                    (.user.login // "unknown")
                ]
                | @tsv
              end
        '
}

seen_subjects=$'\n'
seen_pull_requests=$'\n'
separator=$'\x1f'

emit_release_note() {
    local short_hash="$1"
    local subject="$2"
    local username="$3"
    local suffix="${4:-}"
    local normalized_subject
    local display_subject

    is_release_note "$subject" || return 0
    normalized_subject="$(printf '%s' "$subject" | tr '[:upper:]' '[:lower:]' | sed -E 's/[[:space:]]+/ /g; s/[[:space:].]+$//')"
    [[ "$seen_subjects" != *$'\n'"$normalized_subject"$'\n'* ]] || return 0
    seen_subjects+="${normalized_subject}"$'\n'

    display_subject="$(printf '%s' "$subject" | sed -E 's/[[:space:]]+$//; s/\.$//')"
    printf '%s %s%s @%s  \n' "$short_hash" "$display_subject" "$suffix" "$username"
}

if [[ "$repository_history" == true ]]; then
    while IFS="$separator" read -r commit short_hash subject author_name author_email parents; do
        [[ -n "$commit" ]] || continue
        is_excluded_hash "$commit" && continue

        pull_request=""
        if [[ "$offline" == false ]]; then
            pull_request="$(resolve_pull_request "$commit")"
        fi

        if [[ -n "$pull_request" ]]; then
            IFS=$'\t' read -r pull_number pull_title pull_author <<< "$pull_request"
            [[ "$seen_pull_requests" != *$'\n'"$pull_number"$'\n'* ]] || continue
            seen_pull_requests+="${pull_number}"$'\n'
            emit_release_note "$short_hash" "$pull_title" "$pull_author" " (#${pull_number})"
            continue
        fi

        [[ "$parents" != *" "* ]] || continue
        username="$(resolve_username "$commit" "$author_name" "$author_email")"
        emit_release_note "$short_hash" "$subject" "$username"
    done < <(
        git log "${from_ref}..${to_ref}" --first-parent \
            --format="%H${separator}%h${separator}%s${separator}%an${separator}%ae${separator}%P"
    )
else
    while IFS="$separator" read -r commit short_hash subject author_name author_email; do
        [[ -n "$commit" ]] || continue
        is_excluded_hash "$commit" && continue
        username="$(resolve_username "$commit" "$author_name" "$author_email")"
        emit_release_note "$short_hash" "$subject" "$username"
    done < <(
        git log "${from_ref}..${to_ref}" --no-merges \
            --format="%H${separator}%h${separator}%s${separator}%an${separator}%ae"
    )
fi
