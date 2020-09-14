package com.teletracker.common.elasticsearch.util

import com.teletracker.common.db.model.{OfferType, PresentationType}
import com.teletracker.common.elasticsearch.model.{EsAvailability, EsItem}
import org.scalatest.flatspec.AnyFlatSpec

class ItemUpdateApplierTest extends AnyFlatSpec {
  import io.circe.syntax._

  "ItemUpdateApplier" should "work" in {
    val newAvailability = ItemUpdateApplier.applyAvailabilityDelta(
      TestEsItem.item,
      Nil,
      Set(
        EsAvailability.AvailabilityKey(
          1,
          "US",
          OfferType.Subscription.toString,
          Some(PresentationType.HD.toString)
        ),
        EsAvailability.AvailabilityKey(
          1,
          "US",
          OfferType.Subscription.toString,
          Some(PresentationType.SD.toString)
        )
      )
    )

    println(TestEsItem.item.availability.asJson.spaces2)
    println(newAvailability.asJson.spaces2)
  }
}

private object TestEsItem {
  final private val json =
    """
      |{
      |          "adult" : false,
      |          "availability" : [
      |            {
      |              "network_id" : 1,
      |              "last_updated" : "2020-07-14T16:38:44.117-06:00",
      |              "last_updated_by" : "IngestNetflixCatalogDelta",
      |              "presentation_type" : "sd",
      |              "crawler" : "netflix",
      |              "crawl_version" : 1594537240,
      |              "network_name" : "netflix",
      |              "region" : "US",
      |              "offer_type" : "subscription"
      |            },
      |            {
      |              "network_id" : 1,
      |              "last_updated" : "2020-07-14T16:38:44.117-06:00",
      |              "last_updated_by" : "IngestNetflixCatalogDelta",
      |              "presentation_type" : "hd",
      |              "crawler" : "netflix",
      |              "crawl_version" : 1594537240,
      |              "network_name" : "netflix",
      |              "region" : "US",
      |              "offer_type" : "subscription"
      |            }
      |          ],
      |          "cast" : [
      |            {
      |              "character" : "Daniel",
      |              "name" : "Ralph Macchio",
      |              "id" : "1cf67093-ca8f-43ec-934c-f41ff3c1ae4c",
      |              "slug" : "ralph-macchio-1961",
      |              "order" : 0
      |            },
      |            {
      |              "character" : "Miyagi",
      |              "name" : "Pat Morita",
      |              "id" : "c4604a7f-7745-449d-85ec-cf7055074ac0",
      |              "slug" : "pat-morita-1932",
      |              "order" : 1
      |            },
      |            {
      |              "character" : "Ali",
      |              "name" : "Elisabeth Shue",
      |              "id" : "3244c33b-ccdd-41ec-b355-0bf3500ef9e2",
      |              "slug" : "elisabeth-shue-1963",
      |              "order" : 2
      |            },
      |            {
      |              "character" : "Kreese",
      |              "name" : "Martin Kove",
      |              "id" : "78bde4cb-348f-4cda-8700-64dfa927d2c3",
      |              "slug" : "martin-kove-1947",
      |              "order" : 3
      |            },
      |            {
      |              "character" : "Lucille",
      |              "name" : "Randee Heller",
      |              "id" : "2eaa40b0-2728-4c3c-b480-40392c6386a1",
      |              "slug" : "randee-heller-1947",
      |              "order" : 4
      |            },
      |            {
      |              "character" : "Johnny",
      |              "name" : "William Zabka",
      |              "id" : "f4e7b4de-b246-4822-9057-36d19df611db",
      |              "slug" : "william-zabka-1965",
      |              "order" : 5
      |            },
      |            {
      |              "character" : "Bobby",
      |              "name" : "Ron Thomas",
      |              "id" : "24771fe8-4c1c-408a-a6af-f3696ec3d6b4",
      |              "slug" : "ron-thomas-1961",
      |              "order" : 6
      |            },
      |            {
      |              "character" : "Tommy",
      |              "name" : "Rob Garrison",
      |              "id" : "d9235bba-3b3c-4e1a-8ad9-d1ecd721d78f",
      |              "slug" : "rob-garrison-1960",
      |              "order" : 7
      |            },
      |            {
      |              "character" : "Dutch",
      |              "name" : "Chad McQueen",
      |              "id" : "0ac6710d-db04-432f-b1a0-48dd09eab830",
      |              "slug" : "chad-mcqueen-1960",
      |              "order" : 8
      |            },
      |            {
      |              "character" : "Jimmy",
      |              "name" : "Tony O'Dell",
      |              "id" : "b3a30505-523c-4a85-846e-45746cdb965d",
      |              "slug" : "tony-odell-1960",
      |              "order" : 9
      |            },
      |            {
      |              "character" : "Freddy",
      |              "name" : "Israel Juarbe",
      |              "id" : "4bf52fff-780b-4edd-96aa-3a5ac3204a14",
      |              "slug" : "israel-juarbe-1963",
      |              "order" : 10
      |            },
      |            {
      |              "character" : "Mr. Mills",
      |              "name" : "William Bassett",
      |              "id" : "e6cdc63d-a277-46fe-b588-1ebb8db415a5",
      |              "slug" : "william-bassett-1935",
      |              "order" : 11
      |            },
      |            {
      |              "character" : "Jerry",
      |              "name" : "Larry B. Scott",
      |              "id" : "da2b6f6a-133b-4ec4-a3ad-4540a9433fd0",
      |              "slug" : "larry-b-scott-1961",
      |              "order" : 12
      |            },
      |            {
      |              "character" : "Susan",
      |              "name" : "Juli Fields",
      |              "id" : "c54ada48-e0a3-4f4c-97e2-9d1d9af330a4",
      |              "slug" : "juli-fields",
      |              "order" : 13
      |            },
      |            {
      |              "character" : "Barbara",
      |              "name" : "Dana Andersen",
      |              "id" : "44968082-e3ec-42d7-8bad-7316aa6f3564",
      |              "slug" : "dana-andersen",
      |              "order" : 14
      |            },
      |            {
      |              "character" : "Chucky",
      |              "name" : "Frank Burt Avalon",
      |              "id" : "b6cbb35e-8f08-42b9-9566-63d4c37fd6d5",
      |              "slug" : "frank-burt-avalon-1963",
      |              "order" : 15
      |            },
      |            {
      |              "character" : "Billy",
      |              "name" : "Jeff Fishman",
      |              "id" : "7c2004f4-ca16-469b-904d-dff8d3326b69",
      |              "slug" : "jeff-fishman",
      |              "order" : 16
      |            },
      |            {
      |              "character" : "Chris",
      |              "name" : "Ken Daly",
      |              "id" : "3dd3be98-05c6-4e89-bcb1-8850c7d6cb12",
      |              "slug" : "ken-daly",
      |              "order" : 17
      |            },
      |            {
      |              "character" : "Alan",
      |              "name" : "Tom Fridley",
      |              "id" : "ba493350-e3a5-49bb-a238-aa7e92ad1136",
      |              "slug" : "tom-fridley",
      |              "order" : 18
      |            },
      |            {
      |              "character" : "Referee",
      |              "name" : "Pat E. Johnson",
      |              "id" : "96bd6cfe-17ca-47bb-b564-fb700e315299",
      |              "slug" : "pat-e-johnson",
      |              "order" : 19
      |            },
      |            {
      |              "character" : "Ring Announcer",
      |              "name" : "Bruce Malmuth",
      |              "id" : "ec808ccc-7377-4c60-90a2-e17be6ccc907",
      |              "slug" : "bruce-malmuth-1934",
      |              "order" : 20
      |            },
      |            {
      |              "character" : "Karate Semi-Finalist",
      |              "name" : "Darryl Vidal",
      |              "id" : "28fe29e3-5fb9-42f1-b209-3416dbd6ec79",
      |              "slug" : "darryl-vidal",
      |              "order" : 21
      |            },
      |            {
      |              "character" : "Lady with Dog",
      |              "name" : "Frances Bay",
      |              "id" : "24cf83b4-6f54-456d-9003-ffa43bcaa202",
      |              "slug" : "frances-bay-1919",
      |              "order" : 22
      |            },
      |            {
      |              "character" : "Official",
      |              "name" : "Christopher Kriesa",
      |              "id" : "3991bfc1-578e-4af6-9010-0c42c13175ca",
      |              "slug" : "christopher-kriesa-1949",
      |              "order" : 23
      |            },
      |            {
      |              "character" : "Mr. Harris",
      |              "name" : "Bernie Kuby",
      |              "id" : "7a9f4680-1181-4dfa-ac6a-6dc1f2caa653",
      |              "slug" : "bernie-kuby",
      |              "order" : 24
      |            },
      |            {
      |              "character" : "Restaurant Manager",
      |              "name" : "Joan Lemmo",
      |              "id" : "04d00969-215f-43ae-a8b8-f579fbd310ef",
      |              "slug" : "joan-lemmo",
      |              "order" : 25
      |            },
      |            {
      |              "character" : "Cashier",
      |              "name" : "Helen Siff",
      |              "id" : "4294930a-b241-4d0f-8b01-349f7aae2e36",
      |              "slug" : "helen-siff",
      |              "order" : 26
      |            },
      |            {
      |              "character" : "Yahoo #1",
      |              "name" : "Larry Drake",
      |              "id" : "8b966a63-aa15-4d01-a3e9-cf35e133a0ba",
      |              "slug" : "larry-drake-1950",
      |              "order" : 27
      |            },
      |            {
      |              "character" : "Yahoo #2",
      |              "name" : "David Abbott",
      |              "id" : "8dab4bb6-1dcb-49f4-8e34-732f2dc08c9c",
      |              "slug" : "david-abbott",
      |              "order" : 28
      |            },
      |            {
      |              "character" : "Cheerleading Coach",
      |              "name" : "Molly Basler",
      |              "id" : "b3328760-69d8-4616-b8f8-05c41d25293e",
      |              "slug" : "molly-basler",
      |              "order" : 29
      |            },
      |            {
      |              "character" : "Waiter",
      |              "name" : "David De Lange",
      |              "id" : "49935a18-10c6-4e26-b884-33e1d7559e8a",
      |              "slug" : "david-de-lange",
      |              "order" : 31
      |            },
      |            {
      |              "character" : "Karate Student",
      |              "name" : "Erik Felix",
      |              "id" : "553fee21-79cd-40c2-9700-f20f59c2892d",
      |              "slug" : "erik-felix",
      |              "order" : 32
      |            },
      |            {
      |              "character" : "Soccer Coach",
      |              "name" : "Peter Jason",
      |              "id" : "12dc317f-c40c-455f-9959-c3f5a2c3a8a9",
      |              "slug" : "peter-jason-1944",
      |              "order" : 33
      |            },
      |            {
      |              "character" : "Chicken Boy",
      |              "name" : "Todd Lookinland",
      |              "id" : "b274c3fa-5d90-4ffb-a5bf-7a6f7284a581",
      |              "slug" : "todd-lookinland",
      |              "order" : 34
      |            },
      |            {
      |              "character" : "Referee #2",
      |              "name" : "Clarence McGee Jr.",
      |              "id" : "c2f1d223-b01b-458e-b576-6958e60d51c4",
      |              "slug" : "clarence-mcgee-jr",
      |              "order" : 35
      |            },
      |            {
      |              "character" : "Doctor",
      |              "name" : "William Norren",
      |              "id" : "da4b1b17-d1a0-4874-a561-98beb8cb8278",
      |              "slug" : "william-norren",
      |              "order" : 36
      |            },
      |            {
      |              "character" : "Referee #3",
      |              "name" : "Sam Scarber",
      |              "id" : "ba83e362-26c4-4413-8545-8db88d14e7b8",
      |              "slug" : "sam-scarber-1949",
      |              "order" : 37
      |            },
      |            {
      |              "character" : "Eddie",
      |              "name" : "Scott Strader",
      |              "id" : "960ccf89-67d5-4358-8122-91e7c54fe86a",
      |              "slug" : "scott-strader",
      |              "order" : 38
      |            },
      |            {
      |              "character" : "Tournament Guest (uncredited)",
      |              "name" : "Chris Casamassa",
      |              "id" : "7f48f656-9c6b-4282-b5d6-42c87cd585a2",
      |              "slug" : "chris-casamassa-1963",
      |              "order" : 39
      |            },
      |            {
      |              "character" : "Karate Student (uncredited)",
      |              "name" : "Donald DeNoyer",
      |              "id" : "8dbb7119-f679-40f0-9a94-81dfb1822883",
      |              "slug" : "donald-denoyer",
      |              "order" : 40
      |            },
      |            {
      |              "character" : "Karate Fan (uncredited)",
      |              "name" : "Charles Gallant",
      |              "id" : "10211e19-d57c-4596-b9f6-e64e6296a89e",
      |              "slug" : "charles-gallant",
      |              "order" : 41
      |            },
      |            {
      |              "character" : "Karate Fan (uncredited)",
      |              "name" : "Katheryn Gallant",
      |              "id" : "47d1426f-8ce5-4a94-9cb1-7379c1f7595f",
      |              "slug" : "katheryn-gallant",
      |              "order" : 42
      |            },
      |            {
      |              "character" : "Karate Fan (uncredited)",
      |              "name" : "Kelly Gallant",
      |              "id" : "b58cacd0-e21d-4643-a9fd-7dec23de2715",
      |              "slug" : "kelly-gallant",
      |              "order" : 43
      |            },
      |            {
      |              "character" : "Guy at Halloween Dance (uncredited)",
      |              "name" : "David LeBell",
      |              "id" : "562174d9-edc0-40ce-a439-b9507e64f563",
      |              "slug" : "david-lebell",
      |              "order" : 44
      |            },
      |            {
      |              "character" : "Cheering Kid (uncredited)",
      |              "name" : "Tom Levy",
      |              "id" : "150ffea8-715b-452d-97d0-a2391c08bd44",
      |              "slug" : "tom-levy",
      |              "order" : 45
      |            },
      |            {
      |              "character" : "Waiter (uncredited)",
      |              "name" : "Freeman Love",
      |              "id" : "e07b20f4-b213-45d4-b3da-c72c0c71158f",
      |              "slug" : "freeman-love",
      |              "order" : 46
      |            },
      |            {
      |              "character" : "Club Patron (uncredited)",
      |              "name" : "Monty O'Grady",
      |              "id" : "16186fbf-3394-42d3-9b3a-58889d5b3daa",
      |              "slug" : "monty-ogrady-1916",
      |              "order" : 47
      |            },
      |            {
      |              "character" : "Beachgoer (uncredited)",
      |              "name" : "Richard Patrick",
      |              "id" : "ec136559-0e75-40cb-83d5-d29306b35c61",
      |              "slug" : "richard-patrick",
      |              "order" : 48
      |            },
      |            {
      |              "character" : "Student at Dance (uncredited)",
      |              "name" : "Stan Rodarte",
      |              "id" : "a40bbbc0-aecb-4a51-b3b6-8d2c83c7f993",
      |              "slug" : "stan-rodarte-1954",
      |              "order" : 49
      |            },
      |            {
      |              "character" : "Member of Cobra Kai (uncredited)",
      |              "name" : "Andrew Shue",
      |              "id" : "0b6ea6ad-97ec-40f0-9897-0f99b79c8e78",
      |              "slug" : "andrew-shue-1967",
      |              "order" : 50
      |            },
      |            {
      |              "character" : "Mrs. Mills (Ali's Mother) (uncredited)",
      |              "name" : "Sharon Spelman",
      |              "id" : "1742a145-99ad-4014-b446-ac4e1cf05150",
      |              "slug" : "sharon-spelman-1942",
      |              "order" : 51
      |            },
      |            {
      |              "character" : "Club Patron (uncredited)",
      |              "name" : "Milanka Stevens",
      |              "id" : "ad62866f-7a78-4641-ac3f-20f8226154b3",
      |              "slug" : "milanka-stevens",
      |              "order" : 52
      |            },
      |            {
      |              "character" : "Club Patron (uncredited)",
      |              "name" : "Nick Stevens",
      |              "id" : "fa442dbb-ebf4-4ac8-8fe7-9bda78b2bd8f",
      |              "slug" : "nick-stevens",
      |              "order" : 53
      |            },
      |            {
      |              "character" : "Karate Fan #4 (uncredited)",
      |              "name" : "Duff Tallahassee",
      |              "id" : "d059d5b9-77a5-43f5-8f7a-5e01dae844ee",
      |              "slug" : "duff-tallahassee",
      |              "order" : 55
      |            },
      |            {
      |              "character" : "Club Patron (uncredited)",
      |              "name" : "Robert Strong",
      |              "id" : "a7bb37ad-1604-42c3-b73c-871d10d588d4",
      |              "slug" : "robert-strong-1906",
      |              "order" : 54
      |            }
      |          ],
      |          "crew" : [
      |            {
      |              "name" : "Michael Muscarella",
      |              "id" : "94698217-94cb-41a5-86e9-f295bc5ba4eb",
      |              "department" : "Art",
      |              "job" : "Construction Coordinator",
      |              "slug" : "michael-muscarella"
      |            },
      |            {
      |              "name" : "William J. Cassidy",
      |              "id" : "6939e126-c152-4ab1-98ef-78ac8d005541",
      |              "department" : "Art",
      |              "job" : "Production Design",
      |              "slug" : "william-j-cassidy"
      |            },
      |            {
      |              "name" : "John H. Anderson",
      |              "id" : "eb67612f-aac2-491a-b677-7b146c3a723f",
      |              "department" : "Art",
      |              "job" : "Set Decoration",
      |              "slug" : "john-h-anderson"
      |            },
      |            {
      |              "name" : "James Crabe",
      |              "id" : "144ef54f-030c-484c-9592-fc87d0fe908f",
      |              "department" : "Camera",
      |              "job" : "Director of Photography",
      |              "slug" : "james-crabe-1931"
      |            },
      |            {
      |              "name" : "Aida Swinson",
      |              "id" : "d88ae636-7f7b-4f5e-97c3-5c2ec3ed7b0b",
      |              "department" : "Costume & Make-Up",
      |              "job" : "Costume Design",
      |              "slug" : "aida-swinson"
      |            },
      |            {
      |              "name" : "Richard Bruno",
      |              "id" : "28eeac9b-ec8c-4d10-af32-3f934885ac59",
      |              "department" : "Costume & Make-Up",
      |              "job" : "Costume Design",
      |              "slug" : "richard-bruno-1924"
      |            },
      |            {
      |              "name" : "Cheri Ruff",
      |              "id" : "7ea93a32-e66a-4780-945f-89d02fce39a3",
      |              "department" : "Costume & Make-Up",
      |              "job" : "Hairstylist",
      |              "slug" : "cheri-ruff"
      |            },
      |            {
      |              "name" : "Frank Toro",
      |              "id" : "4c672e84-c918-4ff1-b130-80908b6c0930",
      |              "department" : "Crew",
      |              "job" : "Special Effects",
      |              "slug" : "frank-toro"
      |            },
      |            {
      |              "name" : "John G. Avildsen",
      |              "id" : "e063a800-65a0-4b15-bf00-66941a76608c",
      |              "department" : "Directing",
      |              "job" : "Director",
      |              "slug" : "john-g-avildsen-1935"
      |            },
      |            {
      |              "name" : "Bud S. Smith",
      |              "id" : "eda79f40-6981-41b5-b09b-53a88b305b36",
      |              "department" : "Editing",
      |              "job" : "Editor",
      |              "slug" : "bud-s-smith"
      |            },
      |            {
      |              "name" : "John G. Avildsen",
      |              "id" : "e063a800-65a0-4b15-bf00-66941a76608c",
      |              "department" : "Editing",
      |              "job" : "Editor",
      |              "slug" : "john-g-avildsen-1935"
      |            },
      |            {
      |              "name" : "Walt Mulconery",
      |              "id" : "ba1c4673-9371-4072-8246-e5e34bc580e8",
      |              "department" : "Editing",
      |              "job" : "Editor",
      |              "slug" : "walt-mulconery-1932"
      |            },
      |            {
      |              "name" : "Bonnie Timmermann",
      |              "id" : "03ed3ee2-f5e5-4ad2-8e0b-8f9d59fa629c",
      |              "department" : "Production",
      |              "job" : "Casting",
      |              "slug" : "bonnie-timmermann"
      |            },
      |            {
      |              "name" : "Caro Jones",
      |              "id" : "5acdaab7-d440-48ce-a41e-569fad0ddf46",
      |              "department" : "Production",
      |              "job" : "Casting",
      |              "slug" : "caro-jones"
      |            },
      |            {
      |              "name" : "Pennie DuPont",
      |              "id" : "26ab87eb-d59a-4451-bd01-620d6f7f2be5",
      |              "department" : "Production",
      |              "job" : "Casting",
      |              "slug" : "pennie-dupont"
      |            },
      |            {
      |              "name" : "Jerry Weintraub",
      |              "id" : "40881373-24a3-4b33-9da9-4917fde13e76",
      |              "department" : "Production",
      |              "job" : "Producer",
      |              "slug" : "jerry-weintraub-1937"
      |            },
      |            {
      |              "name" : "Bill Conti",
      |              "id" : "7532689b-64fe-4d01-b702-ca8d6ae831ca",
      |              "department" : "Sound",
      |              "job" : "Original Music Composer",
      |              "slug" : "bill-conti-1942"
      |            },
      |            {
      |              "name" : "Robert Mark Kamen",
      |              "id" : "2870d6ac-efd7-4cb4-aabf-9d12af0a6cfb",
      |              "department" : "Writing",
      |              "job" : "Writer",
      |              "slug" : "robert-mark-kamen"
      |            }
      |          ],
      |          "external_ids" : [
      |            "imdb__tt0087538",
      |            "tmdb__1885",
      |            "netflix__60036164",
      |            "wikidata__Q846679",
      |            "apple-tv__the-karate-kid/id270980948"
      |          ],
      |          "genres" : [ ],
      |          "id" : "735e23bc-2c55-4c3c-bfd9-05498299bb79",
      |          "images" : [
      |            {
      |              "provider_shortname" : "tmdb",
      |              "provider_id" : 0,
      |              "id" : "/g8Q4QfwO3curRpMqE4JUxPXfL3H.jpg",
      |              "image_type" : "backdrop"
      |            },
      |            {
      |              "provider_shortname" : "tmdb",
      |              "provider_id" : 0,
      |              "id" : "/1mp4ViklKvA0WXXsNvNx0RBuiit.jpg",
      |              "image_type" : "poster"
      |            }
      |          ],
      |          "original_title" : "The Karate Kid",
      |          "overview" : "Hassled by the school bullies, Daniel LaRusso has his share of adolescent woes. Luckily, his apartment building houses a resident martial arts master: Kesuke Miyagi, who agrees to train Daniel ... and ends up teaching him much more than self-defense. Armed with newfound confidence, skill and wisdom, Daniel ultimately faces off against his tormentors in this hugely popular classic underdog tale.",
      |          "popularity" : 89.612,
      |          "ratings" : [
      |            {
      |              "provider_shortname" : "tmdb",
      |              "vote_average" : 7.0,
      |              "weighted_last_generated" : "2020-05-05T00:39:33.439Z",
      |              "provider_id" : 0,
      |              "weighted_average" : 6.81,
      |              "vote_count" : 2351
      |            },
      |            {
      |              "provider_shortname" : "imdb",
      |              "vote_average" : 7.199999809265137,
      |              "weighted_last_generated" : "2020-05-05T00:39:33.439Z",
      |              "provider_id" : 2,
      |              "weighted_average" : 7.19,
      |              "vote_count" : 166045
      |            }
      |          ],
      |          "recommendations" : [
      |            {
      |              "id" : "d91598eb-e79a-4fa4-b3c8-9d0d04347240",
      |              "title" : "The Karate Kid Part II",
      |              "slug" : "the-karate-kid-part-ii-1986"
      |            },
      |            {
      |              "id" : "4fb03081-8e71-4fd0-82c9-0d84cc511def",
      |              "title" : "The Karate Kid Part III",
      |              "slug" : "the-karate-kid-part-iii-1989"
      |            },
      |            {
      |              "id" : "3739fcea-c505-4d45-baa3-60f2c5735601",
      |              "title" : "The Next Karate Kid",
      |              "slug" : "the-next-karate-kid-1994"
      |            },
      |            {
      |              "id" : "dac2c2e3-5917-4a91-9683-a27ba55eec58",
      |              "title" : "Police Academy",
      |              "slug" : "police-academy-1984"
      |            },
      |            {
      |              "id" : "4f9ad568-2e7e-4f84-82c9-08e007fd68a8",
      |              "title" : "Crocodile Dundee",
      |              "slug" : "crocodile-dundee-1986"
      |            },
      |            {
      |              "id" : "886a85fd-485e-48ef-977f-62f1b14b7659",
      |              "title" : "Kindergarten Cop",
      |              "slug" : "kindergarten-cop-1990"
      |            },
      |            {
      |              "id" : "96ef74c0-0e51-4c4e-9011-2e053cc9044e",
      |              "title" : "Twister",
      |              "slug" : "twister-1996"
      |            },
      |            {
      |              "id" : "e8a88c18-84f2-49ed-83fe-f91dadcb6537",
      |              "title" : "Honey, I Shrunk the Kids",
      |              "slug" : "honey-i-shrunk-the-kids-1989"
      |            },
      |            {
      |              "id" : "92e35485-b6e2-479a-bbb2-c2caab67a0b4",
      |              "title" : "Superman II",
      |              "slug" : "superman-ii-1980"
      |            },
      |            {
      |              "id" : "3038d1ef-4c3e-4bb4-8cb7-f269bcb336e4",
      |              "title" : "Beverly Hills Cop II",
      |              "slug" : "beverly-hills-cop-ii-1987"
      |            },
      |            {
      |              "id" : "d47d9e6c-3615-4e91-be7b-fd1590943c19",
      |              "title" : "Twins",
      |              "slug" : "twins-1988"
      |            },
      |            {
      |              "id" : "79fe0839-36aa-4dd5-8e23-12db87ad7e55",
      |              "title" : "Coming to America",
      |              "slug" : "coming-to-america-1988"
      |            },
      |            {
      |              "id" : "24f653ee-8e0e-4ac1-8695-ff625c5171e6",
      |              "title" : "Short Circuit",
      |              "slug" : "short-circuit-1986"
      |            },
      |            {
      |              "id" : "19652a3f-4483-4172-a909-d1ad692fe7ec",
      |              "title" : "Big",
      |              "slug" : "big-1988"
      |            },
      |            {
      |              "id" : "73e7ea77-f4a7-4170-9df5-c04138beaf59",
      |              "title" : "Teenage Mutant Ninja Turtles",
      |              "slug" : "teenage-mutant-ninja-turtles-1990"
      |            },
      |            {
      |              "id" : "3fb19a82-b58d-4651-92bd-622230a13fed",
      |              "title" : "Rocky II",
      |              "slug" : "rocky-ii-1979"
      |            },
      |            {
      |              "id" : "852c3f25-9c7a-4b20-b263-991c8786e0f3",
      |              "title" : "Commando",
      |              "slug" : "commando-1985"
      |            },
      |            {
      |              "id" : "4f1a0a3c-51ad-4ae8-95be-7b77fb70332e",
      |              "title" : "Crocodile Dundee II",
      |              "slug" : "crocodile-dundee-ii-1988"
      |            },
      |            {
      |              "id" : "d81ab987-6ca4-4338-a916-697db8fba5c2",
      |              "title" : "The Golden Child",
      |              "slug" : "the-golden-child-1986"
      |            },
      |            {
      |              "id" : "fb7eb2a1-42ee-48fb-ba57-de2239a56e08",
      |              "title" : "Beverly Hills Cop",
      |              "slug" : "beverly-hills-cop-1984"
      |            }
      |          ],
      |          "release_date" : "1984-06-22",
      |          "release_dates" : [
      |            {
      |              "country_code" : "DE",
      |              "release_date" : "1984-11-09",
      |              "certification" : "12"
      |            },
      |            {
      |              "country_code" : "AR",
      |              "release_date" : "1984-10-04",
      |              "certification" : ""
      |            },
      |            {
      |              "country_code" : "NL",
      |              "release_date" : "1984-10-11",
      |              "certification" : "12"
      |            },
      |            {
      |              "country_code" : "SE",
      |              "release_date" : "1984-10-19",
      |              "certification" : "11"
      |            },
      |            {
      |              "country_code" : "US",
      |              "release_date" : "1984-06-22",
      |              "certification" : "PG"
      |            },
      |            {
      |              "country_code" : "GB",
      |              "release_date" : "1984-08-31",
      |              "certification" : "PG"
      |            },
      |            {
      |              "country_code" : "IT",
      |              "release_date" : "1984-10-25",
      |              "certification" : "T"
      |            },
      |            {
      |              "country_code" : "FR",
      |              "release_date" : "1984-11-26",
      |              "certification" : "U"
      |            },
      |            {
      |              "country_code" : "BR",
      |              "release_date" : "1984-07-05",
      |              "certification" : "10"
      |            },
      |            {
      |              "country_code" : "PT",
      |              "release_date" : "1984-10-25",
      |              "certification" : "M/12"
      |            },
      |            {
      |              "country_code" : "AU",
      |              "release_date" : "1984-09-27",
      |              "certification" : "PG"
      |            }
      |          ],
      |          "runtime" : 126,
      |          "slug" : "the-karate-kid-1984",
      |          "tags" : null,
      |          "title" : "The Karate Kid",
      |          "type" : "movie",
      |          "last_updated" : 1600075435932,
      |          "alternative_titles" : [
      |            {
      |              "country_code" : "IT",
      |              "title" : "Karate Kid 1 - Per vincere domani",
      |              "type" : ""
      |            },
      |            {
      |              "country_code" : "IT",
      |              "title" : "Karate Kid I - Per vincere domani",
      |              "type" : ""
      |            },
      |            {
      |              "country_code" : "IT",
      |              "title" : "Per Vincere Domani - The Karate Kid",
      |              "type" : ""
      |            },
      |            {
      |              "country_code" : "IT",
      |              "title" : "The Karate Kid - Per vincere domani",
      |              "type" : ""
      |            },
      |            {
      |              "country_code" : "PT",
      |              "title" : "Momento da Verdade",
      |              "type" : ""
      |            },
      |            {
      |              "country_code" : "SE",
      |              "title" : "Karate Kid - sanningens ögonblick",
      |              "type" : ""
      |            },
      |            {
      |              "country_code" : "US",
      |              "title" : "The Karate Kid Part 1",
      |              "type" : ""
      |            }
      |          ],
      |          "videos" : [
      |            {
      |              "provider_shortname" : "tmdb",
      |              "language_code" : "en",
      |              "country_code" : "US",
      |              "provider_source_id" : "556cba02c3a3685748001fb9",
      |              "size" : 360,
      |              "video_source_id" : "yDi3an8WgN4",
      |              "name" : "The Karate Kid (1984) Trailer",
      |              "provider_id" : 0,
      |              "video_source" : "youtube",
      |              "video_type" : "Trailer"
      |            }
      |          ]
      |        }""".stripMargin

  import io.circe._
  import io.circe.parser._
  val item = decode[EsItem](json).right.get
}
