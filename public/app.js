const form = document.querySelector("#search-form");
const keywordInput = document.querySelector("#keyword");
const searchButton = document.querySelector("#search-button");
const resultCount = document.querySelector("#result-count");
const statusMessage = document.querySelector("#status-message");
const resultList = document.querySelector("#result-list");

form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const keyword = keywordInput.value.trim();
    if (!keyword) {
        setStatus("검색어를 입력해주세요.", true);
        keywordInput.focus();
        return;
    }

    setLoading(true);
    setStatus("조회 중입니다.", false);
    resultList.replaceChildren();
    resultCount.textContent = "0건";

    try {
        const params = new URLSearchParams({
            query: keyword,
            limit: "10",
            sort: "date"
        });
        const response = await fetch(`/api/blogs/refresh?${params.toString()}`, {
            method: "POST"
        });
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || "조회에 실패했습니다.");
        }

        renderResults(data.items || []);
        resultCount.textContent = `${data.count || 0}건`;
        setStatus(data.count > 0 ? `"${data.query}" 검색 결과입니다.` : "검색 결과가 없습니다.", false);
    } catch (error) {
        setStatus(error.message, true);
    } finally {
        setLoading(false);
    }
});

function renderResults(items) {
    const fragment = document.createDocumentFragment();

    items.forEach((item) => {
        const card = document.createElement("article");
        card.className = "post-card";

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

        card.append(title, meta, description, link);
        fragment.append(card);
    });

    resultList.replaceChildren(fragment);
}

function setStatus(message, isError) {
    statusMessage.textContent = message;
    statusMessage.classList.toggle("error", isError);
}

function setLoading(isLoading) {
    searchButton.disabled = isLoading;
    searchButton.textContent = isLoading ? "조회 중" : "조회";
}

function formatPostDate(value) {
    if (!value || value.length !== 8) {
        return "작성일 미상";
    }
    return `${value.slice(0, 4)}.${value.slice(4, 6)}.${value.slice(6, 8)}`;
}
