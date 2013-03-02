window.onload = function() {
    var pfx = location.pathname.match(/(.*)\/(.*)$/)[1]

    var map = L.map('map').setView([39.8, -113.4], 5);
    L.tileLayer('http://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}.jpg', {
        attribution: 'Map data &copy; ESRI',
        maxZoom: 18,
        format: 'image/jpeg'
    }).addTo(map);

    var d = null;
    function updateWMS() {
        if (d) {
            map.removeLayer(d);
        }

        var spread = document.getElementById('spread').value;

        var imageUrl = '/test?bbox=0&width=0&height=0';
        var imageBounds = [[39.856, -75.312], [40.130, -74.936]];

        d = L.imageOverlay(imageUrl, imageBounds, { opacity: 0.5 });

        d.addTo(map);
    }

    var button = document.getElementById('update')
    button.addEventListener('click', updateWMS);

    updateWMS();
}
