import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  CardMedia,
  Checkbox,
  createStyles,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormGroup,
  FormHelperText,
  IconButton,
  Input,
  InputAdornment,
  InputLabel,
  makeStyles,
  Theme,
} from '@material-ui/core';
import { Cancel, Check, PlaylistAdd } from '@material-ui/icons';
import _ from 'lodash';
import * as R from 'ramda';
import {
  LIST_ADD_ITEM_INITIATED,
  LIST_RETRIEVE_ALL_INITIATED,
  useCreateList,
  USER_SELF_CREATE_LIST,
  useUpdateListTracking,
} from '../../actions/lists';
import { AppState } from '../../reducers';
import { Item, itemBelongsToLists } from '../../types/v2/Item';
import CreateAListValidator from '../../utils/validation/CreateAListValidator';
import AuthDialog from '../Auth/AuthDialog';
import { useWithUserContext } from '../../hooks/useWithUser';
import useStateSelector, { useStateSelectorWithPrevious } from '../../hooks/useStateSelector';
import createDeepEqSelector from '../../hooks/createDeepEqSelector';
import { usePrevious } from '../../hooks/usePrevious';
import { useStateDeepEq } from '../../hooks/useStateDeepEq';
import { hookDeepEqual } from '../../hooks/util';
import useNewListValidation from '../../hooks/useNewListValidation';
import { ResponsiveImage } from '../ResponsiveImage';
import useIsMobile from '../../hooks/useIsMobile';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    dialogContainer: {
      display: 'flex',
    },
    formControl: {
      margin: theme.spacing(3),
    },
    button: {
      margin: theme.spacing(1),
      whiteSpace: 'nowrap',
    },
    leftIcon: {
      marginRight: theme.spacing(1),
    },
    spacer: {
      display: 'flex',
      flexGrow: 1,
    },
    title: {
      backgroundColor: theme.palette.primary.main,
      padding: theme.spacing(1, 2),
    },
  }),
);

const possibleListsSelector = createDeepEqSelector(
  (state: AppState) => state.lists.listsById,
  listsById => _.filter(listsById, list => !list.isDynamic && !list.isDeleted),
);

const allListIdsSelector = createDeepEqSelector(
  (state: AppState) => state.lists.listsById,
  listsById => Object.keys(listsById),
);

interface Props {
  open: boolean;
  onClose: () => void;
  item: Item;
}

export default function AddToListDialog(props: Props) {
  const classes = useStyles();
  const isMobile = useIsMobile();
  const { isLoggedIn } = useWithUserContext();

  const wasOpen = usePrevious(props.open);

  const listsToShow = useStateSelector(possibleListsSelector);
  const allListIds = useStateSelector(allListIdsSelector);
  const [listsLoading, wereListsLoading] = useStateSelectorWithPrevious(
    state => state.lists.loading[LIST_RETRIEVE_ALL_INITIATED],
  );
  const [
    addToListLoading,
    wasAddToListLoading,
  ] = useStateSelectorWithPrevious(state =>
    R.defaultTo(false)(state.lists.loading[LIST_ADD_ITEM_INITIATED]),
  );
  const [
    createAListLoading,
    wasCreateAListLoading,
  ] = useStateSelectorWithPrevious(state =>
    R.defaultTo(false)(state.userSelf.loading[USER_SELF_CREATE_LIST]),
  );

  const calculateListChanges = () => {
    const belongsToLists: string[] = itemBelongsToLists(props.item);

    return _.reduce(
      allListIds,
      (acc, elem) => {
        return {
          ...acc,
          [elem]: belongsToLists.includes(elem),
        };
      },
      {},
    );
  };

  const [originalListState, setOriginalListState] = useState(
    calculateListChanges(),
  );
  const [actionPending, setActionPending] = useState(false);
  const [listChanges, setListChanges] = useState(calculateListChanges());
  const [createAListEnabled, setCreateAListEnabled] = useState(false);
  const [newListValidation, setNewListValidation] = useStateDeepEq(
    CreateAListValidator.defaultState().asObject(),
    hookDeepEqual,
  );
  const [newListName, setNewListName] = useState('');
  const validateNewList = useNewListValidation();

  const dispatchUpdateLists = useUpdateListTracking();
  const dispatchCreateList = useCreateList();

  useEffect(() => {
    if (!listsLoading) {
      setOriginalListState(calculateListChanges());
      setListChanges(calculateListChanges());
    }
  }, []);

  useEffect(() => {
    if (wereListsLoading && !listsLoading) {
      setOriginalListState(calculateListChanges());
      setListChanges(calculateListChanges());
    }
  }, [listsLoading, wereListsLoading]);

  useEffect(() => {
    if (props.open && !wasOpen) {
      setOriginalListState(calculateListChanges());
      setListChanges(calculateListChanges());
    }
  }, [props.open]);

  useEffect(() => {
    if (wasAddToListLoading && !addToListLoading) {
      setActionPending(false);
    } else if (!wasAddToListLoading && addToListLoading) {
      setActionPending(true);
    }
  }, [addToListLoading]);

  useEffect(() => {
    if (wasCreateAListLoading && !createAListLoading) {
      setCreateAListEnabled(false);
      setNewListName('');
      setNewListValidation(CreateAListValidator.defaultState().asObject());
    }
  }, [createAListLoading]);

  const handleModalClose = useCallback(() => {
    setCreateAListEnabled(false);
    props.onClose();
  }, []);

  const listContainsItem = (listId: string, item: Item) => {
    return itemBelongsToLists(item).includes(listId);
  };

  const handleSubmit = useCallback(() => {
    const addedToLists = _.filter(
      allListIds,
      listId => listChanges[listId] && !listContainsItem(listId, props.item),
    );
    const removedFromLists = _.filter(
      allListIds,
      listId => !listChanges[listId] && listContainsItem(listId, props.item),
    );

    console.log(addedToLists, removedFromLists);

    dispatchUpdateLists({
      itemId: props.item.id,
      addToLists: addedToLists,
      removeFromLists: removedFromLists,
    });

    handleModalClose();
  }, [listChanges, props.item]);

  const handleCheckboxChange = useCallback(
    (listId: string, checked: boolean) => {
      setListChanges(prev => {
        return {
          ...prev,
          [listId]: checked,
        };
      });
    },
    [],
  );

  const toggleCreateAList = useCallback(() => {
    setCreateAListEnabled(prev => !prev);
    setNewListName('');
  }, []);

  const updateListName = useCallback(
    (ev: React.ChangeEvent<HTMLInputElement>) => {
      setNewListName(ev.target.value);
    },
    [newListName],
  );

  const createNewList = useCallback(() => {
    let result = validateNewList(newListName);

    if (result.hasError()) {
      setNewListValidation(result.asObject());
    } else {
      dispatchCreateList({ name: newListName });
    }
  }, [allListIds, newListName, newListValidation]);

  const hasTrackingChanged = useMemo(() => {
    return _.isEqual(originalListState, listChanges);
  }, [originalListState, listChanges]);

  const renderCreateNewListSection = () => {
    let { nameDuplicateError, nameLengthError } = newListValidation;

    return (
      <React.Fragment>
        <FormControl disabled={createAListLoading}>
          <InputLabel>New list</InputLabel>
          <Input
            type="text"
            value={newListName}
            onChange={updateListName}
            error={nameDuplicateError || nameLengthError}
            fullWidth
            disabled={createAListLoading}
            endAdornment={
              <React.Fragment>
                <InputAdornment position="end">
                  <IconButton
                    size="small"
                    disableRipple
                    onClick={toggleCreateAList}
                  >
                    <Cancel />
                  </IconButton>
                </InputAdornment>
                <InputAdornment position="end">
                  <IconButton
                    size="small"
                    onClick={createNewList}
                    disableRipple
                  >
                    <Check />
                  </IconButton>
                </InputAdornment>
              </React.Fragment>
            }
          />
          <FormHelperText
            id="component-error-text"
            style={{
              display: nameDuplicateError || nameLengthError ? 'block' : 'none',
            }}
          >
            {nameLengthError ? 'List name cannot be blank' : null}
            {nameDuplicateError
              ? 'You already have a list with this name'
              : null}
          </FormHelperText>
        </FormControl>
      </React.Fragment>
    );
  };

  if (isLoggedIn) {
    return (
      <Dialog
        aria-labelledby="update-tracking-dialog"
        aria-describedby="update-tracking-dialog"
        open={props.open}
        onClose={handleModalClose}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle id="update-tracking-dialog" className={classes.title}>
          Add or Remove {props.item.canonicalTitle} from your lists
        </DialogTitle>

        <DialogContent className={classes.dialogContainer}>
          <FormGroup style={{ flex: 1 }}>
            {_.map(listsToShow, list => (
              <FormControlLabel
                key={list.id}
                control={
                  <Checkbox
                    onChange={(_, checked) =>
                      handleCheckboxChange(list.id, checked)
                    }
                    checked={listChanges[list.id] ? true : false}
                    color="primary"
                  />
                }
                label={list.name}
              />
            ))}
            {createAListEnabled ? renderCreateNewListSection() : null}
          </FormGroup>
          {!isMobile ? (
            <div style={{ flex: '0.5' }}>
              <CardMedia
                item={props.item}
                component={ResponsiveImage}
                imageType="poster"
                imageStyle={{
                  width: '100%',
                  boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
                }}
              />
            </div>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button
            variant="contained"
            className={classes.button}
            disabled={createAListEnabled}
            onClick={toggleCreateAList}
          >
            <PlaylistAdd className={classes.leftIcon} />
            New List
          </Button>
          <div className={classes.spacer} />
          <Button onClick={handleModalClose} className={classes.button}>
            Cancel
          </Button>
          <Button
            disabled={hasTrackingChanged}
            onClick={handleSubmit}
            color="primary"
            variant="contained"
            className={classes.button}
          >
            Save
          </Button>
        </DialogActions>
      </Dialog>
    );
  } else {
    return <AuthDialog open={props.open} onClose={handleModalClose} />;
  }
}
