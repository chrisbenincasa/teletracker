import * as R from 'ramda';
import { ListsByIdMap } from '../../reducers/lists';

export interface CreateAListValidationStateObj {
  nameLengthError: boolean;
  nameDuplicateError: boolean;
}

export class CreateAListValidationState {
  nameLengthError: boolean;
  nameDuplicateError: boolean;

  constructor(nameLengthError: boolean, nameDuplicateError: boolean) {
    this.nameLengthError = nameLengthError;
    this.nameDuplicateError = nameDuplicateError;
  }

  asObject(): CreateAListValidationStateObj {
    return {
      nameLengthError: this.nameLengthError,
      nameDuplicateError: this.nameDuplicateError,
    };
  }

  hasError() {
    return this.nameLengthError || this.nameDuplicateError;
  }
}

export default class CreateAListValidator {
  static defaultState(): CreateAListValidationState {
    return new CreateAListValidationState(false, false);
  }

  static validate(
    existingLists: ListsByIdMap,
    listName: string,
  ): CreateAListValidationState {
    let nameExists = function(element) {
      return listName.toLowerCase() === element.name.toLowerCase();
    };

    let state = this.defaultState();

    if (listName.length === 0) {
      state.nameLengthError = true;
    } else if (R.values(existingLists).some(nameExists)) {
      state.nameDuplicateError = true;
    }

    return state;
  }
}
