const form = document.querySelector("#search-form");
const keywordInput = document.querySelector("#keyword");
const searchButton = document.querySelector("#search-button");
const loadMoreButton = document.querySelector("#load-more-button");
const resultCount = document.querySelector("#result-count");
const statusMessage = document.querySelector("#status-message");
const resultList = document.querySelector("#result-list");

const pageSize = 10;
const seenLinks = new Set();
let currentKeyword = "";
let nextStart = 1;
let renderedCount = 0;
let isLoading = false;

form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const keyword = keywordInput.value.trim();
    if (!keyword) {
        setStatus("검색어를 입력해주세요.", true);
        keywordInput.focus();
        return;
    }

    currentKeyword = keyword;
    nextStart = 1;
    renderedCount = 0;
    seenLinks.clear();
    resultList.replaceChildren();
    resultCount.textContent = "0건";
    loadMoreButton.hidden = true;

    await fetchPage({ reset: true });
});

loadMoreButton.addEventListener("click", async () => {
    await fetchPage({ reset: false });
});

async function fetchPage({ reset }) {
    if (isLoading) {
        return;
    }

    setLoading(true);
    setStatus(reset ? "조회 중입니다." : "다음 글을 불러오는 중입니다.", false);

    try {
        const params = new URLSearchParams({
            query: currentKeyword,
            limit: String(pageSize),
            start: String(nextStart),
            sort: "date"
        });
        const response = await fetch(`/api/blogs/refresh?${params.toString()}`, {
            method: "POST"
        });
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || "조회에 실패했습니다.");
        }

        const newItems = filterNewItems(data.items || []);
        renderResults(newItems, { append: !reset });

        renderedCount += newItems.length;
        nextStart = data.nextStart || nextStart + pageSize;
        resultCount.textContent = `${renderedCount}건`;

        const hasMore = (data.items || []).length === pageSize && nextStart <= 1000;
        loadMoreButton.hidden = !hasMore;

        if (renderedCount === 0) {
            setStatus("검색 결과가 없습니다.", false);
        } else if (newItems.length === 0 && hasMore) {
            setStatus("이번 페이지는 이미 본 글이어서 제외했습니다. 더보기를 한 번 더 눌러주세요.", false);
        } else {
            setStatus(`"${data.query}" 검색 결과입니다. 중복 글은 자동 제외됩니다.`, false);
        }
    } catch (error) {
        setStatus(error.message, true);
    } finally {
        setLoading(false);
    }
}

function filterNewItems(items) {
    return items.filter((item) => {
        const key = item.link || item.title;
        if (!key || seenLinks.has(key)) {
            return false;
        }
        seenLinks.add(key);
        return true;
    });
}

function renderResults(items, { append }) {
    const fragment = document.createDocumentFragment();

    items.forEach((item) => {
        const card = document.createElement("article");
        card.className = "post-card";

        const media = document.createElement("a");
        media.className = item.imageUrl ? "post-media" : "post-media post-media-empty";
        media.href = item.link;
        media.target = "_blank";
        media.rel = "noreferrer";

        if (item.imageUrl) {
            const image = document.createElement("img");
            image.src = `/api/images?url=${encodeURIComponent(item.imageUrl)}`;
            image.alt = item.title || "블로그 대표 이미지";
            image.loading = "lazy";
            image.addEventListener("error", () => {
                image.remove();
                media.classList.add("post-media-empty");
                media.textContent = "N";
            });
            media.append(image);
        } else {
            media.textContent = "N";
        }

        const content = document.createElement("div");
        content.className = "post-content";

        const title = document.createElement("h3");
        title.className = "post-title";
        const titleLink = document.createElement("a");
        titleLink.href = item.link;
        titleLink.target = "_blank";
        titleLink.rel = "noreferrer";
        titleLink.textContent = item.title || "제목 없음";
        title.append(titleLink);

        const meta = document.createElement("div");
        meta.className = "post-meta";
        const blogger = document.createElement("span");
        blogger.textContent = item.bloggerName || "블로그";
        const date = document.createElement("span");
        date.textContent = formatPostDate(item.postDate);
        meta.append(blogger, date);

        const description = document.createElement("p");
        description.className = "post-description";
        description.textContent = item.description || "요약이 없습니다.";

        const link = document.createElement("a");
        link.className = "post-link";
        link.href = item.link;
        link.target = "_blank";
        link.rel = "noreferrer";
        link.textContent = "원문 보기";

        content.append(title, meta, description, link);
        card.append(media, content);
        fragment.append(card);
    });

    if (append) {
        resultList.append(fragment);
    } else {
        resultList.replaceChildren(fragment);
    }
}

function setStatus(message, isError) {
    statusMessage.textContent = message;
    statusMessage.classList.toggle("error", isError);
}

function setLoading(loading) {
    isLoading = loading;
    searchButton.disabled = loading;
    loadMoreButton.disabled = loading;
    searchButton.textContent = loading ? "조회 중" : "조회";
    loadMoreButton.textContent = loading ? "불러오는 중" : "더보기";
}

function formatPostDate(value) {
    if (!value || value.length !== 8) {
        return "작성일 미상";
    }
    return `${value.slice(0, 4)}.${value.slice(4, 6)}.${value.slice(6, 8)}`;
}
