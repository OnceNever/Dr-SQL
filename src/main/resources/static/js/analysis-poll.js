(function () {
    document.querySelectorAll("form[data-disable-on-submit]").forEach(function (form) {
        form.addEventListener("submit", function () {
            const button = form.querySelector("button[type='submit']");
            if (!button) {
                return;
            }
            button.disabled = true;
            if (button.dataset.submittingText) {
                button.textContent = button.dataset.submittingText;
            }
        });
    });

    const detailRecordId = document.body.dataset.analysisRecordId;
    if (detailRecordId) {
        const timer = window.setInterval(function () {
            fetch(`/api/slow-sql/${detailRecordId}`, {headers: {"Accept": "application/json"}})
                .then(function (response) {
                    return response.ok ? response.json() : null;
                })
                .then(function (record) {
                    if (!record || record.analysisStatus !== "ANALYZING") {
                        window.clearInterval(timer);
                        window.location.reload();
                    }
                })
                .catch(function () {
                    window.clearInterval(timer);
                });
        }, 3000);
    }

    if (document.body.dataset.analysisWatch === "true") {
        const timer = window.setInterval(function () {
            fetch("/api/slow-sql", {headers: {"Accept": "application/json"}})
                .then(function (response) {
                    return response.ok ? response.json() : [];
                })
                .then(function (records) {
                    const stillAnalyzing = records.some(function (record) {
                        return record.analysisStatus === "ANALYZING";
                    });
                    if (!stillAnalyzing) {
                        window.clearInterval(timer);
                        window.location.reload();
                    }
                })
                .catch(function () {
                    window.clearInterval(timer);
                });
        }, 3000);
    }
})();
