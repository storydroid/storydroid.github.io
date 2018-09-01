var nodes = {};

// Compute the distinct nodes from the links.
links.forEach(function (link) {
    link.source = nodes[link.source] || (nodes[link.source] = {
        name: link.source,
        actcode: link.source_actcode,
        layoutcode: link.source_layoutcode,
        img: link.sourceimg,
        methodLinks: link.methodSourceLink
    });
    link.target = nodes[link.target] || (nodes[link.target] = {
        name: link.target,
        actcode: link.target_actcode,
        layoutcode: link.target_layoutcode,
        img: link.targetimg,
        methodLinks: link.methodTargetLink
    });
});

var wholeCode = document.getElementById("one_pre");
var methodCode = document.getElementById("two_pre");

var width = 725,
    height = 700;

var force = d3.layout.force()
    .nodes(d3.values(nodes))
    .links(links)
    .size([width, height])
    .linkDistance(300)
    .on("tick", tick)
    .gravity(0.03)
    .charge(-300)
    .linkStrength(0.1)
    .start();

var svg = d3.select("#vis").append("svg")
    .attr("width", width)
    .attr("height", height);

// Per-type markers, as they don't inherit styles.
svg.append("defs").selectAll("marker")
    .data(["suit", "licensing", "resolved"])
    .enter().append("marker")
    .attr("id", function (d) {
        return d;
    })
    .attr("viewBox", "0 -5 10 10")
    .attr("refX", 15)
    .attr("refY", -1.5)
    .attr("markerWidth", 6)
    .attr("markerHeight", 6)
    .attr("orient", "auto")
    .append("path")
    .attr("d", "M0,-5L10,0L0,5");

var path = svg.append("g").selectAll("path")
                    .data(force.links())
                    .enter().append("path")
                    .attr("class", function (d) {
                        return "link " + d.type;
                    })
                    .attr("marker-end", function (d) {
                        return "url(#" + d.type + ")";
                    });    
                    
var image = svg.append("g").selectAll("image")
    .data(force.nodes())
    .enter().append("image")
    .attr("xlink:href", function (d) {        
	return d.img;
    })
    .attr("id", (d) => d.name)
    .attr("height", 100)
    .attr("width", 80)
    .attr("x", -10)
    .attr("y", -8)
    .on("mouseover", (d) => {
        d3.select("#" + d.name).attr("class", "")
	.attr("height", 200)
    	.attr("width", 160);
    })
    .on("mouseleave", (d) => {
        d3.select("#" + d.name).attr("class", "")
	.attr("height", 100)
    	.attr("width", 80);
    })
    .on("click", (d) => {
	d.fixed = true;
        wholeCode.innerHTML = d.actcode;
        methodCode.innerHTML = d.layoutcode;
        
        document.getElementById("vis_method").innerHTML = "";
        var method_nodes = {};
        var method_links = Array.from(d.methodLinks);
        var dummy_links = []
        
        //deep copy
        for (let i = 0; i < method_links.length; i++) {
            let link = method_links[i];
            dummy_links.push({
                source: method_nodes[link.source] || (method_nodes[link.source] = {
                    name: link.source
                }),
                target: method_nodes[link.target] || (method_nodes[link.target] = {
                    name: link.target
                })
            });
        }

        var width = 525,
            height = 700;
        var methodForce = d3.layout.force()
            .nodes(d3.values(method_nodes))
            .links(dummy_links)
            .size([width, height])
            .linkDistance(100)
            .charge(-300)
            .on("tick", tick)
            .start();

        var svg = d3.select("#vis_method").append("svg")
            .attr("width", width)
            .attr("height", height);

        svg.append("defs").selectAll("marker")
            .data(["suit", "licensing", "resolved"])
            .enter().append("marker")
            .attr("id", function (d) {
                return d;
            })
            .attr("viewBox", "0 -5 10 10")
            .attr("refX", 15)
            .attr("refY", -1.5)
            .attr("markerWidth", 6)
            .attr("markerHeight", 6)
            .attr("orient", "auto")
            .append("path")
            .attr("d", "M0,-5L10,0L0,5");

        var path = svg.append("g").selectAll("path")
            .data(methodForce.links())
            .enter().append("path")
            .attr("class", "link")
            .attr("marker-end", "url(#suit)");

        var circle = svg.append("g").selectAll("circle")
            .data(methodForce.nodes())
            .enter().append("circle")
            .attr("r", 6)
            .call(methodForce.drag);

        var text = svg.append("g").selectAll("text")
            .data(methodForce.nodes())
            .enter().append("text")
            .attr("x", 8)
            .attr("y", ".31em")
            .text(function (d) {
                return d.name;
            });

        function tick() {
            path.attr("d", linkArc);
            circle.attr("transform", transform);
            text.attr("transform", transform);
        }

        function linkArc(d) {
            var dx = d.target.x - d.source.x,
                dy = d.target.y - d.source.y,
                dr = Math.sqrt(dx * dx + dy * dy);
            return "M" + d.source.x + "," + d.source.y + "A" + dr + "," + dr + " 0 0,1 " + d.target.x + "," + d.target.y;
        }

        function transform(d) {
            return "translate(" + d.x + "," + d.y + ")";
        }
    })
    .call(force.drag);

    
    var text = svg.append("g").selectAll("text")
        .data(force.nodes())
        .enter().append("text")
        .attr("x", 8)
        .attr("y", -10)
        // .attr("y", ".11em")
	   .style("font-size", "13px")
	   .style("font-weight", "bold")
        .text(function (d) {
            return d.name;
    });

// Use elliptical arc path segments to doubly-encode directionality.
function tick() {
    path.attr("d", linkArc);
    text.attr("transform", transform);
    image.attr("transform", transform);
}

function linkArc(d) {
    var dx = d.target.x - d.source.x,
        dy = d.target.y - d.source.y,
        dr = Math.sqrt(dx * dx + dy * dy);
    return "M" + d.source.x + "," + d.source.y + "A" + dr + "," + dr + " 0 0,1 " + d.target.x + "," + d.target.y;
}

function transform(d) {
    return "translate(" + d.x + "," + d.y + ")";
}