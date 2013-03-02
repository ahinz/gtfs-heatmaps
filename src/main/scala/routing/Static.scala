package router

import javax.ws.rs.core._
import javax.ws.rs._

@Path("/")
class StaticFiles {
  @Path("index.html") @GET def index = static("index.html")
  @Path("main.js") @GET def main = static("main.js")
  @Path("style.css") @GET def style = static("style.css")

  def slurp(f: String) =
    io.Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(f)).mkString

  def static(f: String) = Response.ok(slurp(s"html/$f")).`type`("text/html").build()
}
