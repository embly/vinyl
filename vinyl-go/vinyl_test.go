package vinyl

import "testing"

func TestAllMethod(t *testing.T) {
	db := DB{}

	qs := []Query{}

	if err := db.All(&qs); err != nil {
		t.Error(err)
	}
}
