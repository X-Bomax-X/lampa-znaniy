"""Convert editable course sources to bundled JSON and verify the catalog."""

import argparse
from decimal import Decimal, InvalidOperation
import json
from pathlib import Path
import random
import re
import unicodedata


ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "courses"
ASSETS = ROOT / "app/src/main/assets/subjects"
TEST_COURSE = ROOT / "app/src/debug/assets/subjects/test.json"
SUBJECTS = {
    "math", "russian", "informatics", "physics", "chemistry", "biology",
    "history", "geography", "social", "literature", "english", "german",
    "french", "spanish",
}


def normalized(text):
    return " ".join(unicodedata.normalize("NFC", text).split())


def question_from_line(line, rng, question_id):
    kind, prompt, value, explanation = (part.strip() for part in line.split("|"))
    item = dict(id=question_id, type=kind, prompt=prompt.replace(r"\n", "\n"), explanation=explanation)
    answers = [part.strip() for part in value.split(";")]
    if kind in {"single", "multiple"}:
        choices = [(part.removeprefix("*"), part.startswith("*")) for part in answers]
        rng.shuffle(choices)
        item["choices"] = [text for text, _ in choices]
        item["correct"] = [index for index, (_, correct) in enumerate(choices) if correct]
    elif kind in {"text", "number"}:
        item["accepted"] = answers
    elif kind == "match":
        pairs = [tuple(value.strip() for value in part.split("=", 1)) for part in answers]
        left, right = zip(*pairs)
        item["left"] = list(left)
        item["choices"] = list(right)
        rng.shuffle(item["choices"])
        item["matches"] = [item["choices"].index(value) for value in right]
    else:
        raise ValueError(f"unknown question type {kind}")
    return item


def read_course(path):
    rng = random.Random(path.stem)
    course = {"id": path.stem, "title": "", "lessons": []}
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line:
            continue
        try:
            if line.startswith("# "):
                assert not course["title"], "duplicate title"
                course["title"] = line[2:]
            elif line.startswith("## "):
                course["lessons"].append({
                    "id": f"lesson-{len(course['lessons']) + 1:02}",
                    "title": line[3:],
                    "questions": [],
                })
            else:
                questions = course["lessons"][-1]["questions"]
                questions.append(question_from_line(line, rng, f"q{len(questions) + 1:02}"))
        except (AssertionError, ValueError, IndexError) as error:
            raise ValueError(f"{path.name}:{line_number}: {error}") from error
    return course


def distinct_text(values):
    return all(value.strip() for value in values) and len(set(map(normalized, values))) == len(values)


def validate_course(course, expected_lessons):
    assert course["title"].strip(), "missing course title"
    assert re.fullmatch(r"[a-z]+", course["id"]), "invalid subject id"
    assert len(course["lessons"]) == expected_lessons, "wrong lesson count"
    assert len({lesson["id"] for lesson in course["lessons"]}) == expected_lessons, "duplicate lesson IDs"
    assert distinct_text([lesson["title"] for lesson in course["lessons"]]), "duplicate or empty lesson title"
    prompts = set()
    types = set()
    for lesson in course["lessons"]:
        questions = lesson["questions"]
        assert len(questions) == 5, f"{lesson['id']}: expected five tasks"
        assert len({item["id"] for item in questions}) == 5, f"{lesson['id']}: duplicate question IDs"
        for item in questions:
            label = f"{lesson['id']}/{item['id']}"
            prompt = normalized(item["prompt"])
            assert prompt and prompt not in prompts, f"{label}: empty or duplicate question"
            prompts.add(prompt)
            assert item["explanation"].strip(), f"{label}: missing explanation"
            kind = item["type"]
            types.add(kind)
            if kind in {"single", "multiple"}:
                count = len(item["choices"])
                assert count >= 3 and distinct_text(item["choices"]), f"{label}: bad choices"
                correct = set(item["correct"])
                assert correct <= set(range(count)), f"{label}: answer index out of range"
                assert len(correct) == len(item["correct"]), f"{label}: duplicate answer index"
                assert len(correct) == 1 if kind == "single" else 1 < len(correct) < count, label
            elif kind == "match":
                count = len(item["left"])
                assert 2 <= count == len(item["choices"]), f"{label}: wrong pair count"
                assert distinct_text(item["left"]) and distinct_text(item["choices"]), f"{label}: duplicate pair"
                assert sorted(item["matches"]) == list(range(count)), f"{label}: missing pair"
            else:
                assert kind in {"number", "text"}, f"{label}: invalid type"
                assert item["accepted"] and distinct_text(item["accepted"]), f"{label}: bad short answers"
                if kind == "number":
                    try:
                        assert all(Decimal(value.replace("−", "-").replace(",", ".")).is_finite()
                                   for value in item["accepted"])
                    except InvalidOperation as error:
                        raise AssertionError(f"{label}: invalid number") from error
    return types


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="regenerate JSON from the course sources")
    args = parser.parse_args()
    sources = {path.stem: path for path in SOURCES.glob("*.txt")}
    assert set(sources) == SUBJECTS, f"catalog mismatch: {set(sources) ^ SUBJECTS}"
    test_types = validate_course(json.loads(TEST_COURSE.read_text(encoding="utf-8")), 2)
    assert test_types == {"single", "multiple", "text", "number", "match"}
    if args.write:
        ASSETS.mkdir(parents=True, exist_ok=True)
    total = 0
    for subject, source in sorted(sources.items()):
        course = read_course(source)
        try:
            validate_course(course, 10)
        except AssertionError as error:
            raise AssertionError(f"{subject}: {error}") from error
        output = ASSETS / f"{subject}.json"
        if args.write:
            output.write_text(json.dumps(course, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        assert json.loads(output.read_text(encoding="utf-8")) == course, f"{subject}: regenerate JSON"
        total += sum(len(lesson["questions"]) for lesson in course["lessons"])
    assert {path.stem for path in ASSETS.glob("*.json")} == SUBJECTS, "unexpected bundled course"
    print(f"OK: {len(SUBJECTS)} subjects, {total} questions, plus a 10-question test course")


if __name__ == "__main__":
    main()
