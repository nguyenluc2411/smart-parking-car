from app import plate


def test_normalize_uppercases_and_strips_whitespace():
    assert plate.normalize("  51f-123.45 ") == "51F-12345"  # dấu chấm bị bỏ (dotless)
    assert plate.normalize(None) is None


def test_valid_plates():
    assert plate.is_valid("51F-12345")
    assert plate.is_valid("51F-123.45")


def test_invalid_plates():
    assert not plate.is_valid("not-a-plate")
    assert not plate.is_valid("51F12345")  # missing dash
    assert not plate.is_valid(None)


def test_canonicalize_reconstructs_missing_dash():
    # real OCR drops the structural dash / cosmetic dot -> rebuild to a valid plate
    assert plate.canonicalize("51F12345") == "51F-12345"
    assert plate.canonicalize("51F 12345") == "51F-12345"
    assert plate.canonicalize("51F 123.45") == "51F-12345"
    assert plate.canonicalize("51LD12345") == "51LD-12345"


def test_canonicalize_keeps_already_valid_plate():
    assert plate.canonicalize("51F-12345") == "51F-12345"
    assert plate.canonicalize("  51f-123.45 ") == "51F-12345"


def test_canonicalize_strips_ocr_noise():
    assert plate.canonicalize("'51F-12345'") == "51F-12345"


def test_canonicalize_fixes_char_confusion():
    # O/0 and I/1 in digit positions, position-aware
    assert plate.canonicalize("3OA-I2345") == "30A-12345"
    assert plate.canonicalize("5IF-I2B45") == "51F-12845"  # I->1, B->8 in digit positions
    # digits mis-read in the (letter) series position -> only VALID VN series letters
    assert plate.canonicalize("11A-22345") == "11A-22345"
    assert plate.canonicalize("112-22345") == "11Z-22345"  # series 2 -> Z (valid)
    # '0' in the series position must NOT become 'O' (VN plates never use O/I/J/Q/R/W):
    # it can't map to a valid letter, so the read is left invalid for burst/another frame to resolve.
    assert not plate.is_valid(plate.canonicalize("110-22345"))


def test_canonicalize_unparseable_returns_cleaned_invalid():
    result = plate.canonicalize("garbage")
    assert not plate.is_valid(result)
    assert plate.canonicalize(None) is None
