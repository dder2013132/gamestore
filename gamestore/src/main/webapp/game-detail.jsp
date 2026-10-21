<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <link rel="stylesheet"
      href="${pageContext.request.contextPath}/css/style.css">
    <title>${game.title} - PLAYPORT</title>
</head>
<body>
    <h1>${game.title}</h1>

    <!-- TODO: 게임 이미지 -->
    <img class="game-detail-image"
     src="${pageContext.request.contextPath}${game.imagePath}"
     alt="${game.title}">
    <!-- TODO: 가격 -->
    <p>価格: ¥${game.price}</p>

    <!-- TODO: 장르 -->
    <p>ジャンル: ${game.genre}</p>
</body>
</html>