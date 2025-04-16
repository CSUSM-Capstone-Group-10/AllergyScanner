package com.example.allergyscanner

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.CheckBox
import android.widget.TextView
import android.widget.ExpandableListView
import android.widget.ImageButton
import com.example.allergytest.R

class AllergenAdapter(
    private val context: Context,
    private val allergenCategories: MutableList<AllergenCategory>,
    private val expandableListView: ExpandableListView //Pass ExpandableListView for expanding/collapsing
) : BaseExpandableListAdapter() {

    override fun getGroup(groupPosition: Int): Any = allergenCategories[groupPosition]
    override fun getChild(groupPosition: Int, childPosition: Int): Any = allergenCategories[groupPosition].items[childPosition]
    override fun getGroupCount(): Int = allergenCategories.size
    override fun getChildrenCount(groupPosition: Int): Int = allergenCategories[groupPosition].items.size
    override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()
    override fun getChildId(groupPosition: Int, childPosition: Int): Long = childPosition.toLong()
    override fun hasStableIds(): Boolean = false

    override fun getGroupView(groupPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_allergen_group, parent, false)
        val category = getGroup(groupPosition) as AllergenCategory
        val categoryCheckbox = view.findViewById<CheckBox>(R.id.categoryCheckbox)
        val categoryName = view.findViewById<TextView>(R.id.categoryName)

        categoryName.text = category.name
        categoryCheckbox.isChecked = category.isSelected

        categoryName.setOnClickListener {
            if (expandableListView.isGroupExpanded(groupPosition)) {
                expandableListView.collapseGroup(groupPosition)
            } else {
                expandableListView.expandGroup(groupPosition, true)
            }
        }

        categoryCheckbox.setOnClickListener {
            category.isSelected = categoryCheckbox.isChecked
            category.items.forEach { it.isSelected = category.isSelected }
            notifyDataSetChanged()
        }

        return view
    }

    override fun getChildView(groupPosition: Int, childPosition: Int, isLastChild: Boolean, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_allergen_child, parent, false)
        val item = getChild(groupPosition, childPosition) as AllergenItem
        val itemCheckbox = view.findViewById<CheckBox>(R.id.itemCheckbox)
        val itemName = view.findViewById<TextView>(R.id.itemName)
        val deleteButton = view.findViewById<ImageButton>(R.id.deleteButton)

        itemName.text = item.name
        itemCheckbox.isChecked = item.isSelected

        itemCheckbox.setOnClickListener {
            item.isSelected = itemCheckbox.isChecked
        }

        val category = getGroup(groupPosition) as AllergenCategory
        if (category.name == "Other") {
            deleteButton.visibility = View.VISIBLE
            deleteButton.setOnClickListener {
                category.items.removeAt(childPosition)
                notifyDataSetChanged()
            }
        } else {
            deleteButton.visibility = View.GONE
        }

        return view
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true

    fun updateData(newAllergenCategories: List<AllergenCategory>) {
        allergenCategories.clear()
        allergenCategories.addAll(newAllergenCategories)
        notifyDataSetChanged()
    }
}
