import {
  Button,
  CircularProgress,
  createStyles,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControl,
  FormControlLabel,
  Grid,
  IconButton,
  InputLabel,
  LinearProgress,
  ListItemIcon,
  makeStyles,
  Menu,
  MenuItem,
  Select,
  Switch,
  TextField,
  Theme,
  Typography,
} from '@material-ui/core';
import { Delete, Edit, Settings } from '@material-ui/icons';
import _ from 'lodash';
import React, { useCallback, useContext, useEffect, useState } from 'react';
import InfiniteScroll from 'react-infinite-scroller';
import {
  deleteList,
  LIST_RETRIEVE_INITIATED,
  ListRetrieveInitiated,
  ListRetrieveInitiatedPayload,
  updateList,
} from '../actions/lists';
import ItemCard from '../components/ItemCard';
import AllFilters from '../components/Filters/AllFilters';
import ActiveFilters from '../components/Filters/ActiveFilters';
import ShowFiltersButton from '../components/Buttons/ShowFiltersButton';
import { List } from '../types';
import { calculateLimit, getOrInitListOptions } from '../utils/list-utils';
import { FilterParams } from '../utils/searchFilters';
import { optionalSetsEqual } from '../utils/sets';
import { useRouter } from 'next/router';
import useStateSelector, {
  useStateSelectorWithPrevious,
} from '../hooks/useStateSelector';
import { useWithUserContext } from '../hooks/useWithUser';
import { useDispatchAction } from '../hooks/useDispatchAction';
import { useWidth } from '../hooks/useWidth';
import { useDebouncedCallback } from 'use-debounce';
import { createSelector } from 'reselect';
import { AppState } from '../reducers';
import { hookDeepEqual } from '../hooks/util';
import { FilterContext } from '../components/Filters/FilterContext';
import useFilterLoadEffect from '../hooks/useFilterLoadEffect';
import { filterParamsEqual } from '../utils/changeDetection';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    listHeader: {
      margin: theme.spacing(2, 0),
      display: 'flex',
      flex: '1 0 auto',
      alignItems: 'center',
    },
    listName: {
      textDecoration: 'none',
      '&:focus, &:hover, &:visited, &:link &:active': {
        color: theme.palette.text.primary,
      },
    },
    listNameContainer: {
      display: 'flex',
      flex: '1 0 auto',
    },
    loadingCircle: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: 200,
      height: '100%',
    },
    filters: {
      display: 'flex',
      flexDirection: 'row',
      marginBottom: theme.spacing(1),
      justifyContent: 'flex-end',
      alignItems: 'center',
    },
    formControl: {
      margin: theme.spacing(1),
      minWidth: 120,
      display: 'flex',
    },
    listContainer: {
      display: 'flex',
      flexDirection: 'column',
      padding: theme.spacing(0, 2),
      width: '100%',
    },
    noContent: {
      margin: theme.spacing(5),
      width: '100%',
    },
    root: {
      display: 'flex',
      flexGrow: 1,
    },
    textField: {
      margin: theme.spacing(0, 1),
      width: 200,
    },
    title: {
      backgroundColor: theme.palette.primary.main,
      padding: theme.spacing(1, 2),
    },
  }),
);

interface ListDetailDialogProps {
  list?: List;
  openDeleteConfirmation: boolean;
}

interface ListDetailProps {
  preloaded?: boolean;
}

const selectList = createSelector(
  (state: AppState) => state.lists.listsById,
  (_, listId) => listId,
  (listsById, listId) => {
    let currentList: List | undefined = listsById[listId];
    if (!currentList) {
      currentList = _.find(listsById, list =>
        (list.aliases || []).includes(listId),
      );
    }
    return currentList;
  },
);

function ListDetailDialog(props: ListDetailDialogProps) {
  const classes = useStyles();
  const router = useRouter();
  const [deleteConfirmationOpen, setDeleteConfirmationOpen] = useState(
    props.openDeleteConfirmation,
  );
  const [migrateListId, setMigrateListId] = useState('');

  const { isLoggedIn } = useWithUserContext();
  const listsById = useStateSelector(state => state.lists.listsById);
  const { list } = props;

  useEffect(() => {
    setDeleteConfirmationOpen(props.openDeleteConfirmation);
  }, [props.openDeleteConfirmation]);

  const handleMigration = event => {
    setMigrateListId(event.target.value);
  };

  const handleDeleteModalClose = () => {
    setDeleteConfirmationOpen(false);
  };

  const dispatchDeleteList = useDispatchAction(deleteList);

  const handleDeleteList = () => {
    if (isLoggedIn) {
      dispatchDeleteList({
        listId: list!.id,
        mergeListId: migrateListId,
      });

      // TODO: Loading state.
      router.push('/');
    }

    // TODO: Handle this better - tell them they can't.
    handleDeleteModalClose();
  };

  return (
    <div>
      <Dialog
        open={deleteConfirmationOpen}
        onClose={() => setDeleteConfirmationOpen(false)}
        aria-labelledby="alert-dialog-title"
        aria-describedby="alert-dialog-description"
      >
        <DialogTitle id="alert-dialog-title" className={classes.title}>
          {`Delete "${list?.name}" list?`}
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="alert-dialog-description">
            {`Are you sure you want to delete this list? There is no way to undo
              this. ${
                list && list.totalItems > 0
                  ? 'All of the content from your list can be deleted or migrated to another list.'
                  : ''
              }`}
          </DialogContentText>
          {list && list.totalItems > 0 && (
            <FormControl className={classes.formControl}>
              <InputLabel htmlFor="age-simple">
                Migrate tracked items from this list to:
              </InputLabel>
              <Select value={migrateListId} onChange={handleMigration}>
                <MenuItem value="0">
                  <em>Delete all tracked items</em>
                </MenuItem>
                {isLoggedIn &&
                  _.map(
                    listsById,
                    item =>
                      item.id !== list?.id && (
                        <MenuItem key={item.id} value={item.id}>
                          {item.name}
                        </MenuItem>
                      ),
                  )}
              </Select>
            </FormControl>
          )}
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => setDeleteConfirmationOpen(false)}
            color="primary"
          >
            Cancel
          </Button>
          <Button
            onClick={handleDeleteList}
            color="primary"
            variant="contained"
            autoFocus
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </div>
  );
}

function ListDetail(props: ListDetailProps) {
  const classes = useStyles();

  const listBookmark = useStateSelector(state => state.lists.currentBookmark);

  const { userSelf, isLoggedIn } = useWithUserContext();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | undefined>();
  const [deleted, setDeleted] = useState(false);
  const [deleteConfirmationOpen, setDeleteConfirmationOpen] = useState(false);
  const [renameDialogOpen, setRenameDialogOpen] = useState(false);
  const [deleteOnWatch, setDeleteOnWatch] = useState(false);
  const [newListName, setNewListName] = useState('');
  const router = useRouter();
  const width = useWidth();

  const listId = router.query.id as string;

  const listLoading = useStateSelector(
    state => state.lists.loading[LIST_RETRIEVE_INITIATED],
  );

  const [list, previousList] = useStateSelectorWithPrevious(state => {
    return selectList(state, listId);
  }, hookDeepEqual);

  const { filters, defaultFilters } = useContext(FilterContext);

  const [listFilters, setListFilters] = useState<FilterParams | undefined>();
  const [showFilter, setShowFilter] = useState(
    !filterParamsEqual(filters, defaultFilters, defaultFilters?.sortOrder),
  );

  const dispatchUpdateList = useDispatchAction(updateList);
  const dispatchRetrieveList = useDispatchAction(ListRetrieveInitiated);

  const retrieveList = (initialLoad: boolean) => {
    dispatchRetrieveList({
      listId: listId,
      force: true,
      limit: calculateLimit(width, 3),
      ...makeListFilters(initialLoad, filters, listFilters),
      sort: filters.sortOrder,
      itemTypes: filters.itemTypes,
      genres: filters.genresFilter,
      networks: filters.networks,
    });
  };

  const [loadMoreDebounced] = useDebouncedCallback(() => {
    dispatchRetrieveList({
      listId,
      bookmark: listBookmark,
      limit: calculateLimit(width, 3),
      sort: filters.sortOrder,
      itemTypes: filters.itemTypes,
      genres: filters.genresFilter,
      networks: filters.networks,
    });
  }, 200);

  const loadMoreList = useCallback(() => {
    if (listBookmark && !listLoading) {
      loadMoreDebounced();
    }
  }, [listBookmark, listLoading]);

  const makeListFilters = (
    initialLoad: boolean,
    currentFilters: FilterParams,
    initialFilters?: FilterParams,
  ): Partial<ListRetrieveInitiatedPayload> => {
    if (initialLoad) {
      return {};
    } else if (initialFilters) {
      let genres =
        currentFilters.genresFilter &&
        !optionalSetsEqual(
          currentFilters.genresFilter,
          initialFilters.genresFilter,
        )
          ? currentFilters.genresFilter || []
          : initialFilters.genresFilter;

      let networks =
        currentFilters.networks &&
        !optionalSetsEqual(currentFilters.networks, initialFilters.networks)
          ? currentFilters.networks
          : initialFilters.networks;

      let itemTypes = !optionalSetsEqual(
        currentFilters.itemTypes,
        initialFilters.itemTypes,
      )
        ? currentFilters.itemTypes
        : initialFilters.itemTypes;

      return {
        genres,
        networks,
        itemTypes,
      };
    } else {
      return {
        genres: currentFilters.genresFilter,
      };
    }
  };

  const handleRenameList = () => {
    if (userSelf) {
      dispatchUpdateList({
        listId: listId,
        name: newListName,
      });
    }

    setRenameDialogOpen(false);
  };

  const handleRenameChange = event => {
    setNewListName(event.target.value);
  };

  //
  // Effects
  //

  useFilterLoadEffect(
    () => {
      retrieveList(true);
    },
    state => state.lists.currentFilters,
  );

  useEffect(() => {
    console.log(props);
    retrieveList(_.isUndefined(props.preloaded) ? true : !props.preloaded);
  }, [listId]);

  useEffect(() => {
    if (!previousList && list) {
      setDeleteOnWatch(prev => {
        let removeWatchedItemsOption =
          list?.configuration?.options?.removeWatchedItems;
        if (_.isUndefined(removeWatchedItemsOption)) {
          return prev;
        } else {
          return removeWatchedItemsOption;
        }
      });
    }
  }, [list]);

  //
  // State updaters
  //

  const handleMenu = event => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(undefined);
  };

  const setWatchedSetting = () => {
    if (list) {
      let listOptions = getOrInitListOptions(list);
      let newListOptions = {
        ...listOptions,
        removeWatchedItems: !deleteOnWatch,
      };

      dispatchUpdateList({
        listId,
        options: newListOptions,
      });

      setDeleteOnWatch(prev => !prev);
    }
  };

  const handleRenameModalOpen = () => {
    handleClose();
    setRenameDialogOpen(true);
  };

  const toggleFilters = () => {
    setShowFilter(prev => !prev);
  };

  //
  // Render
  //
  const renderLoadingCircle = () => {
    return (
      <div className={classes.loadingCircle}>
        <div>
          <CircularProgress color="secondary" />
        </div>
      </div>
    );
  };

  const renderNoContentMessage = (list: List) => {
    if (list.totalItems > 0 && !listLoading) {
      return 'Sorry, nothing matches your current filter.  Please update and try again.';
    } else if (list.totalItems === 0 && !listLoading && !list.isDynamic) {
      return 'Nothing has been added to this list yet.';
    } else if (list.totalItems === 0 && !listLoading && list.isDynamic) {
      return 'Nothing currently matches this Smart Lists filtering criteria';
    }
  };

  const renderRenameDialog = (list: List) => {
    if (!list) {
      return;
    }

    return (
      <div>
        <Dialog
          open={renameDialogOpen}
          onClose={() => setRenameDialogOpen(false)}
          aria-labelledby="alert-dialog-title"
          aria-describedby="alert-dialog-description"
        >
          <DialogTitle id="alert-dialog-title" className={classes.title}>
            {`Update "${list?.name}" List Name?`}
          </DialogTitle>
          <DialogContent>
            <DialogContentText id="alert-dialog-description">
              Naming is hard, update your list name:
            </DialogContentText>
            <FormControl className={classes.formControl}>
              <TextField
                label="List Name"
                defaultValue={list.name}
                className={classes.textField}
                margin="normal"
                onChange={handleRenameChange}
              />
            </FormControl>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setRenameDialogOpen(false)}>Cancel</Button>
            <Button
              onClick={handleRenameList}
              color="primary"
              variant="contained"
              autoFocus
            >
              Update
            </Button>
          </DialogActions>
        </Dialog>
      </div>
    );
  };

  const renderProfileMenu = () => {
    if (!isLoggedIn) {
      return null;
    }

    if (!list?.ownedByRequester) {
      return null;
    }

    let isMenuOpen = !_.isUndefined(anchorEl);

    return (
      <div>
        <IconButton
          aria-owns={isMenuOpen ? 'material-appbar' : undefined}
          aria-haspopup="true"
          color="inherit"
          onClick={handleMenu}
        >
          <Settings />
          <Typography variant="srOnly">Settings</Typography>
        </IconButton>
        <Menu
          anchorEl={anchorEl}
          anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
          transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          open={isMenuOpen}
          onClose={handleClose}
          disableAutoFocusItem
        >
          <MenuItem onClick={handleRenameModalOpen}>
            <ListItemIcon>
              <Edit />
            </ListItemIcon>
            Rename List
          </MenuItem>
          <MenuItem onClick={() => setDeleteConfirmationOpen(true)}>
            <ListItemIcon>
              <Delete />
            </ListItemIcon>
            Delete List
          </MenuItem>
          <MenuItem>
            <FormControlLabel
              control={
                <Switch
                  checked={deleteOnWatch}
                  onChange={setWatchedSetting}
                  value="checkedB"
                  color="primary"
                />
              }
              label="Automatically remove items after watching"
            />
          </MenuItem>
        </Menu>
      </div>
    );
  };

  const renderListDetail = (list: List) => {
    if ((!listLoading && !list) || deleted) {
      router.replace('/');
    }

    return (
      <div className={classes.root}>
        <div className={classes.listContainer}>
          <div className={classes.listHeader}>
            <div className={classes.listNameContainer}>
              <Typography
                variant="h4"
                align="left"
                className={classes.listName}
              >
                {list.name}
              </Typography>
            </div>
            <ShowFiltersButton onClick={toggleFilters} />
            {renderProfileMenu()}
          </div>
          <div className={classes.filters}>
            <ActiveFilters isListDynamic={list.isDynamic} variant="default" />
          </div>
          <AllFilters
            open={showFilter}
            isListDynamic={list.isDynamic}
            listFilters={listFilters}
          />
          <InfiniteScroll
            pageStart={0}
            loadMore={loadMoreList}
            hasMore={!_.isUndefined(listBookmark)}
            useWindow
            threshold={300}
          >
            <Grid container spacing={2}>
              {(list?.items?.length || 0) > 0 ? (
                list?.items!.map(item => {
                  return (
                    <ItemCard
                      key={item.id}
                      itemId={item.id}
                      listContext={list}
                      withActionButton
                      showDelete={!list.isDynamic}
                    />
                  );
                })
              ) : (
                <Typography
                  align="center"
                  color="secondary"
                  display="block"
                  className={classes.noContent}
                >
                  {renderNoContentMessage(list)}
                </Typography>
              )}
            </Grid>
            {listLoading && renderLoadingCircle()}
          </InfiniteScroll>
        </div>
        <ListDetailDialog
          list={list}
          openDeleteConfirmation={deleteConfirmationOpen}
        />
        {renderRenameDialog(list)}
      </div>
    );
  };

  const renderLoading = () => {
    return (
      <div style={{ display: 'flex' }}>
        <div style={{ flexGrow: 1 }}>
          <LinearProgress />
        </div>
      </div>
    );
  };

  return !list ? renderLoading() : renderListDetail(list!);
}

// DEBUG only.
// ListDetailF.whyDidYouRender = true;

export default React.memo(ListDetail, _.isEqual);
